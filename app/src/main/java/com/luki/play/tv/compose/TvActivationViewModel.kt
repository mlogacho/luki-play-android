// tv/compose/TvActivationViewModel.kt
package com.luki.play.tv.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.TvAuthRepository
import com.luki.play.data.auth.TvPollResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class TvActivationUiState(
    val code: String = "------",
    val activationUrl: String = "https://lukiplay.com/activar",
    val secsLeft: Int = 300,
    /** Backend caído / sin red: se reintenta solo. */
    val error: Boolean = false,
    /** One-shot: emparejado y sesión iniciada → ir al Home de TV. */
    val authenticated: Boolean = false,
)

/**
 * Emparejamiento de TV (`tv-activation.tsx`): crea una sesión, muestra el
 * código + QR con cuenta regresiva y sondea cada 3 s. Al expirar (local o del
 * backend) renueva la sesión; al autenticarse, navega.
 *
 * Todo el ciclo vive en una sola corrutina que itera; el ticker de la cuenta
 * regresiva es hijo de cada iteración vía [coroutineScope], así que renovar la
 * sesión no se cancela a sí mismo (a diferencia de cancelar un Job desde
 * dentro de él).
 */
@HiltViewModel
class TvActivationViewModel @Inject constructor(
    private val repository: TvAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TvActivationUiState())
    val state: StateFlow<TvActivationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { pairingLifecycle() }
    }

    private suspend fun pairingLifecycle() {
        while (coroutineContext.isActive) {
            val session = repository.createSession().getOrNull()
            if (session == null) {
                // Sin red / backend caído: avisa y reintenta en unos segundos.
                _state.update { it.copy(error = true) }
                delay(RETRY_MS)
                continue
            }

            _state.value = TvActivationUiState(
                code = session.code,
                activationUrl = session.activationUrl,
                secsLeft = session.expiresInSeconds,
            )

            val authenticated = coroutineScope {
                val ticker = launch { countdown(session.expiresInSeconds) }
                val result = withTimeoutOrNull(session.expiresInSeconds * 1000L) {
                    pollUntilResolved(session.sessionId)
                }
                ticker.cancel()
                result == TvPollResult.Authenticated
            }

            if (authenticated) {
                _state.update { it.copy(authenticated = true) }
                return
            }
            // Expiró (timeout o "expired"): el while vuelve a crear otra sesión.
        }
    }

    /** Sondea hasta autenticarse o expirar; null si el timeout externo corta. */
    private suspend fun pollUntilResolved(sessionId: String): TvPollResult? {
        while (coroutineContext.isActive) {
            delay(POLL_MS)
            when (repository.poll(sessionId).getOrNull()) {
                TvPollResult.Authenticated -> return TvPollResult.Authenticated
                TvPollResult.Expired -> return TvPollResult.Expired
                else -> Unit // Pending o fallo puntual → siguiente tick
            }
        }
        return null
    }

    private suspend fun countdown(fromSeconds: Int) {
        for (s in (fromSeconds - 1) downTo 0) {
            delay(1000)
            _state.update { it.copy(secsLeft = s) }
        }
    }

    private companion object {
        const val POLL_MS = 3000L
        const val RETRY_MS = 3000L
    }
}
