// feature/favorites/FavoritesViewModel.kt
package com.luki.play.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * "Mi Lista": los canales del catálogo que están marcados como favoritos.
 *
 * Se cruzan los ids favoritos con el catálogo en vez de pedir los canales al
 * endpoint de favoritos, que solo devuelve ids.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    channelsRepository: ChannelsRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> = combine(
        channelsRepository.observeChannels(),
        favoritesRepository.favorites,
    ) { channels, favoriteIds ->
        FavoritesUiState(
            channels = channels.filter { it.id in favoriteIds },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FavoritesUiState(),
    )

    init {
        viewModelScope.launch { favoritesRepository.refresh() }
    }

    fun removeFavorite(channelId: String) {
        viewModelScope.launch { favoritesRepository.toggle(channelId, favorite = false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
