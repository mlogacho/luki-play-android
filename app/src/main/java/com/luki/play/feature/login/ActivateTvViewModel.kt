// feature/login/ActivateTvViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.TvActivateOutcome
import com.luki.play.data.auth.TvAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Activar TV" desde el teléfono, réplica de la sub-pantalla `'tv'` de
 * `frontend/app/(auth)/activar.tsx` (la página que abre el QR del televisor).
 * Manda el código del TV + credenciales; el backend hace el login con el
 * deviceId del TV, así que la sesión del teléfono NO cambia.
 */
@HiltViewModel
class ActivateTvViewModel @Inject constructor(
    private val repository: TvAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivateTvUiState())
    val uiState: StateFlow<ActivateTvUiState> = _state.asStateFlow()

    fun activate(code: String, idNumber: String, password: String) {
        if (_state.value.isLoading) return

        // Mismas validaciones y mensajes que handleActivateTv() del portal.
        val validation = when {
            code.trim().length != 6 -> "El código del TV debe tener 6 caracteres"
            idNumber.isBlank()      -> "Ingresa tu cédula"
            password.isBlank()      -> "Ingresa tu contraseña"
            else                    -> null
        }
        if (validation != null) {
            _state.update { it.copy(errorMessage = validation, infoMessage = null) }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            repository.activateTv(code, idNumber, password)
                .onSuccess { outcome ->
                    when (outcome) {
                        TvActivateOutcome.Connected ->
                            _state.update { it.copy(isLoading = false, connected = true) }
                        // El portal manda a activar la cuenta con este aviso.
                        TvActivateOutcome.NeedsAccountActivation ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    needsAccountActivation = true,
                                    infoMessage = "Tu cuenta aún no está activada. Crea tu contraseña para continuar.",
                                )
                            }
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = AuthErrorMessage.of(
                                t, "No se pudo activar. Verifica el código e intenta de nuevo.",
                            ),
                        )
                    }
                }
        }
    }

    /** El grafo ya navegó a la activación de cuenta; evita re-disparos. */
    fun consumeNeedsAccountActivation() =
        _state.update { it.copy(needsAccountActivation = false) }
}

data class ActivateTvUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** TV conectado → mostrar el estado de éxito. */
    val connected: Boolean = false,
    /** Cuenta con clave temporal → ir a "Activa tu cuenta". */
    val needsAccountActivation: Boolean = false,
)
