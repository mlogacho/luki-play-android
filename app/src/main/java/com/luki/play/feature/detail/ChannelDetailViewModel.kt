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
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
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

    /**
     * Pide reproducir, exigiendo el PIN si el canal está bloqueado.
     *
     * ESPERA a que el canal esté cargado antes de decidir. Antes se leía
     * `_state.value.channel` directamente y, si la carga aún no había
     * terminado, `null?.parentalLocked == true` daba `false`: el control
     * parental se saltaba entero. Con la carga en curso bastaba con pulsar
     * rápido para colarse.
     */
    fun requestPlay() {
        viewModelScope.launch {
            val channel = _state.value.channel
                ?: repository.getChannelById(channelId)?.also { loaded ->
                    _state.value = _state.value.copy(channel = loaded)
                }

            if (channel?.parentalLocked == true && parental.hasPin()) {
                _state.value = _state.value.copy(parentalGateRequired = true)
                return@launch
            }
            fetchStream()
        }
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
                        errorMessage = userMessageFor(t),
                    )
                }
        }
    }

    fun consumePlayRequest() {
        _state.value = _state.value.copy(playRequest = null)
    }

    /**
     * Traduce el fallo a algo que el usuario pueda entender y accionar.
     *
     * Antes se mostraba `t.message` tal cual, así que en pantalla salía
     * "HTTP 401 Unauthorized". El texto de red repite el del login para que
     * la app hable igual en todas partes.
     */
    private fun userMessageFor(t: Throwable): String = when {
        t is IOException ->
            "Sin conexión. Verifica tu internet e intenta de nuevo."
        t is HttpException && t.code() in listOf(401, 403) ->
            "Tu sesión expiró. Vuelve a iniciar sesión."
        t is HttpException && t.code() == 404 ->
            "Este canal ya no está disponible."
        else ->
            "No se pudo obtener la señal. Intenta de nuevo."
    }

    private fun parseManifestType(raw: String?): ManifestType = when (raw?.uppercase()) {
        "HLS"  -> ManifestType.HLS
        "DASH" -> ManifestType.DASH
        else   -> ManifestType.OTHER
    }

    companion object { private const val TAG = "ChannelDetailVM" }
}
