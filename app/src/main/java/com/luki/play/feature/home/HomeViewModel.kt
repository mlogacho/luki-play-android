// feature/home/HomeViewModel.kt
package com.luki.play.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.Slider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado de la pantalla Home.
 *
 * Las filas se construyen agrupando canales por categoría: lo simple
 * mientras no haya un endpoint dedicado de "filas curadas" en el backend.
 */
data class HomeUiState(
    val sliders: List<Slider> = emptyList(),
    val rows: List<ChannelRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

data class ChannelRow(
    val category: String,
    val channels: List<Channel>,
)

/**
 * Marca privada de la VM que mezclamos con el Flow de canales para producir
 * el estado final observable.
 */
private data class TransientState(
    val sliders: List<Slider> = emptyList(),
    val isRefreshing: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ChannelsRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeChannels(),
        transient,
    ) { channels, t ->
        val rows = channels
            .groupBy { it.category }
            .map { (cat, items) -> ChannelRow(cat, items) }
            .sortedBy { it.category }
        HomeUiState(
            sliders      = t.sliders,
            rows         = rows,
            isRefreshing = t.isRefreshing,
            errorMessage = t.errorMessage,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(isRefreshing = true),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            transient.value = transient.value.copy(isRefreshing = true, errorMessage = null)
            val refreshResult = repository.refresh()
            val slidersResult = repository.sliders()
            transient.value = TransientState(
                sliders      = slidersResult.getOrDefault(transient.value.sliders),
                isRefreshing = false,
                errorMessage = listOfNotNull(
                    refreshResult.exceptionOrNull()?.message,
                    slidersResult.exceptionOrNull()?.message,
                ).firstOrNull(),
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
