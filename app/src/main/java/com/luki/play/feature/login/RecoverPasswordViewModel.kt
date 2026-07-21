// feature/login/RecoverPasswordViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Pasos del flujo — espejo del `ForgotForm` del front web. */
enum class RecoverStep { IDENTITY, VERIFY, DONE }

data class RecoverUiState(
    val step: RecoverStep = RecoverStep.IDENTITY,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Aviso no-error (p.ej. "Código reenviado"). */
    val infoMessage: String? = null,
)

/**
 * Recuperación de contraseña por OTP — réplica fiel del front web
 * (`ForgotForm` en app/(auth)/login.tsx):
 *
 * 1. IDENTITY: cédula → `POST /auth/app/request-password-otp`. El backend
 *    responde SIEMPRE igual (anti-enumeración): avanzar a VERIFY no
 *    confirma que la cédula exista.
 * 2. VERIFY: código de 6 + nueva contraseña (política [PasswordPolicy],
 *    idéntica a la web y al backend) → `POST /auth/app/reset-with-otp`.
 * 3. DONE: volver al login.
 */
@HiltViewModel
class RecoverPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoverUiState())
    val uiState: StateFlow<RecoverUiState> = _uiState.asStateFlow()

    /** Cédula validada en el paso 1; la usa el paso 2. */
    private var idNumber: String = ""

    fun requestOtp(rawIdNumber: String) {
        val cedula = rawIdNumber.trim()
        if (cedula.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "La cédula es requerida", infoMessage = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            authRepository.requestPasswordOtp(cedula)
                .onSuccess {
                    idNumber = cedula
                    _uiState.value = if (_uiState.value.step == RecoverStep.VERIFY) {
                        // Reenvío desde el paso 2 — mismo aviso que la web.
                        _uiState.value.copy(isLoading = false, infoMessage = "Código reenviado. Revisa tu correo.")
                    } else {
                        _uiState.value.copy(isLoading = false, step = RecoverStep.VERIFY)
                    }
                }
                .onFailure { t ->
                    Timber.tag(TAG).w(t, "requestOtp falló")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No se pudo solicitar el código",
                    )
                }
        }
    }

    fun resetPassword(rawCode: String, newPassword: String, confirmPassword: String) {
        val code = rawCode.trim()
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "El código debe tener 6 caracteres", infoMessage = null)
            return
        }
        PasswordPolicy.validate(newPassword)?.let { msg ->
            _uiState.value = _uiState.value.copy(errorMessage = msg, infoMessage = null)
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Las contraseñas no coinciden", infoMessage = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            authRepository.resetPasswordWithOtp(idNumber, code, newPassword)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, step = RecoverStep.DONE)
                }
                .onFailure { t ->
                    Timber.tag(TAG).w(t, "resetPassword falló")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No se pudo restablecer la contraseña",
                    )
                }
        }
    }

    /** "Cambiar cédula" — vuelve al paso 1 limpiando avisos, como la web. */
    fun backToIdentity() {
        _uiState.value = _uiState.value.copy(step = RecoverStep.IDENTITY, errorMessage = null, infoMessage = null)
    }

    private companion object { const val TAG = "RecoverVM" }
}
