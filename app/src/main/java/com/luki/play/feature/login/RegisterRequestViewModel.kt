// feature/login/RegisterRequestViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Solicitar acceso" (no-clientes), réplica del `RegisterRequestForm` de
 * `frontend/app/(auth)/login.tsx`. La validación local es la MISMA que la del
 * portal (presencia + cédula ≥ 10 dígitos, SIN checksum: conviven cédulas y RUC);
 * la validación real de cédula/RUC y del correo la hace el backend, y su mensaje
 * (incl. los 409 de "ya eres cliente" / "solicitud pendiente") sube tal cual.
 */
@HiltViewModel
class RegisterRequestViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterRequestUiState())
    val uiState: StateFlow<RegisterRequestUiState> = _state.asStateFlow()

    fun submit(
        nombres: String,
        apellidos: String,
        idNumber: String,
        telefono: String,
        email: String,
        direccion: String,
    ) {
        if (_state.value.isLoading) return

        val validation = when {
            nombres.isBlank()            -> "El nombre es requerido"
            apellidos.isBlank()          -> "Los apellidos son requeridos"
            idNumber.trim().length < 10  -> "La cédula debe tener al menos 10 dígitos"
            telefono.isBlank()           -> "El teléfono es requerido"
            else                         -> null
        }
        if (validation != null) {
            _state.update { it.copy(errorMessage = validation) }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.submitRegistrationRequest(nombres, apellidos, idNumber, telefono, email, direccion)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { t ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = AuthErrorMessage.of(t, "No se pudo enviar la solicitud"))
                    }
                }
        }
    }
}

data class RegisterRequestUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Solicitud aceptada por el backend → mostrar el estado de éxito. */
    val done: Boolean = false,
)
