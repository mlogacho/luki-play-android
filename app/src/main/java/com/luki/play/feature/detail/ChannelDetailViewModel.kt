// feature/detail/ChannelDetailViewModel.kt
package com.luki.play.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.api.CatalogApi
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.parental.ParentalControl
import com.luki.play.player.DrmScheme
import com.luki.play.player.ManifestType
import com.luki.play.player.StreamConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ChannelDetailUiState(
    val channel: Channel? = null,
    val isLoadingStream: Boolean = false,
    val errorMessage: String? = null,
    val playRequest: StreamConfig? = null,
    val parentalGateRequired: Boolean = false,
)

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val repository: ChannelsRepository,
    private val api: CatalogApi,
    private val parental: ParentalControl,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = checkNotNull(savedStateHandle["channelId"]) {
        "ChannelDetailScreen requiere channelId en su ruta"
    }

    private val _state = MutableStateFlow(ChannelDetailUiState())
    val uiState: StateFlow<ChannelDetailUiState> = _state.asStateFlow()

    init {
        loadChannel()
    }

    private fun loadChannel() {
        viewModelScope.launch {
            val channel = repository.getChannelById(channelId)
            _state.value = _state.value.copy(channel = channel)
        }
    }

    fun requestPlay() {
        val channel = _state.value.channel
        // Si el canal está bloqueado por control parental y existe PIN configurado,
        // exigir verificación antes de pedir el stream.
        if (channel?.parentalLocked == true && parental.hasPin()) {
            _state.value = _state.value.copy(parentalGateRequired = true)
            return
        }
        fetchStream()
    }

    /**
     * Llamar tras superar la pantalla parental — continúa el flujo de play.
     */
    fun onParentalVerified() {
        _state.value = _state.value.copy(parentalGateRequired = false)
        fetchStream()
    }

    fun dismissParentalGate() {
        _state.value = _state.value.copy(parentalGateRequired = false)
    }

    private fun fetchStream() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingStream = true, errorMessage = null)
            runCatching { api.getChannelStream(channelId) }
                .onSuccess { dto ->
                    val cfg = StreamConfig(
                        url           = dto.url,
                        title         = _state.value.channel?.name.orEmpty(),
                        manifestType  = parseManifestType(dto.manifestType),
                        drmScheme     = if (dto.drmScheme.equals("WIDEVINE", ignoreCase = true)) DrmScheme.WIDEVINE else DrmScheme.NONE,
                        licenseUrl    = dto.licenseUrl,
                    )
                    _state.value = _state.value.copy(isLoadingStream = false, playRequest = cfg)
                }
                .onFailure { t ->
                    Timber.tag(TAG).w(t, "stream request failed")
                    _state.value = _state.value.copy(
                        isLoadingStream = false,
                        errorMessage = t.message ?: "Error obteniendo stream",
                    )
                }
        }
    }

    fun consumePlayRequest() {
        _state.value = _state.value.copy(playRequest = null)
    }

    private fun parseManifestType(raw: String?): ManifestType = when (raw?.uppercase()) {
        "HLS"  -> ManifestType.HLS
        "DASH" -> ManifestType.DASH
        else   -> ManifestType.OTHER
    }

    companion object { private const val TAG = "ChannelDetailVM" }
}
