// feature/home/HomeViewModel.kt
package com.luki.play.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.Slider
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.favorites.FavoritesRepository
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
    val favorites: Set<String> = emptySet(),
    val user: HomeUser? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

data class ChannelRow(
    val category: String,
    val channels: List<Channel>,
)

/**
 * Datos del usuario que pinta el avatar y la tarjeta del menú de cuenta,
 * equivalentes a `useAuthStore(s => s.user)` en el portal.
 */
data class HomeUser(
    val name: String,
    val email: String,
    val plan: String,
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
    private val favoritesRepository: FavoritesRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    /**
     * Snapshot del usuario en sesión. Se lee una vez: estos datos solo
     * cambian al iniciar sesión, y ese camino ya recrea la pantalla.
     */
    private val currentUser: HomeUser? =
        tokenStore.accessToken()?.let {
            HomeUser(
                name  = tokenStore.displayName().orEmpty(),
                email = tokenStore.email().orEmpty(),
                plan  = tokenStore.plan().orEmpty(),
            )
        }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeChannels(),
        favoritesRepository.favorites,
        transient,
    ) { channels, favorites, t ->
        val rows = channels
            .groupBy { it.category }
            .map { (cat, items) -> ChannelRow(cat, items) }
            .sortedBy { it.category }
        HomeUiState(
            sliders      = t.sliders,
            rows         = rows,
            favorites    = favorites,
            user         = currentUser,
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

    /** Marca o desmarca un canal. La reversión ante error la hace el repo. */
    fun toggleFavorite(channelId: String, favorite: Boolean) {
        viewModelScope.launch { favoritesRepository.toggle(channelId, favorite) }
    }

    fun refresh() {
        viewModelScope.launch {
            transient.value = transient.value.copy(isRefreshing = true, errorMessage = null)
            val refreshResult = repository.refresh()
            val slidersResult = repository.sliders()
            favoritesRepository.refresh()
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
