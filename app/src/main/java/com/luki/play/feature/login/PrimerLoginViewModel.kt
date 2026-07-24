// feature/login/PrimerLoginViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Configura tu cuenta" (primer login), réplica de `PrimerLoginForm` +
 * `VerifyEmailForm` de `frontend/app/(auth)/login.tsx`. Se llega tras un login
 * con clave temporal (`isTempPassword || primerLogin`): el usuario DEBE crear su
 * contraseña permanente. La sesión ya está iniciada, así que los endpoints van
 * autenticados (Bearer).
 *
 * Si al configurar registra un correo nuevo sin verificar, el backend pide
 * verificarlo (paso `VERIFY_EMAIL`, OTP de 6 dígitos), que se puede posponer.
 */
@HiltViewModel
class PrimerLoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PrimerLoginUiState(userName = tokenStore.displayName().orEmpty())
    )
    val uiState: StateFlow<PrimerLoginUiState> = _state.asStateFlow()

    /** Correo tecleado en el paso 1, para reutilizarlo al enviar el OTP. */
    private var pendingEmail: String? = null

    fun completeSetup(newPassword: String, confirmPassword: String, email: String) {
        if (_state.value.isLoading) return

        val pErr = PasswordPolicy.validate(newPassword)
        val validation = when {
            pErr != null -> pErr
            newPassword != confirmPassword -> "Las contraseñas no coinciden"
            else -> null
        }
        if (validation != null) {
            _state.update { it.copy(errorMessage = validation) }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.completePrimerLogin(newPassword, confirmPassword, email)
                .onSuccess { requiresEmailVerification ->
                    pendingEmail = email.trim().takeIf { it.isNotBlank() }
                    if (requiresEmailVerification) {
                        _state.update {
                            it.copy(isLoading = false, step = PrimerLoginStep.VERIFY_EMAIL, errorMessage = null)
                        }
                    } else {
                        _state.update { it.copy(isLoading = false, done = true) }
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = AuthErrorMessage.of(t, "No se pudo completar el registro"))
                    }
                }
        }
    }

    fun sendCode() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.sendEmailVerification(pendingEmail)
                .onSuccess { _state.update { it.copy(isLoading = false, codeSent = true) } }
                .onFailure { t ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = AuthErrorMessage.of(t, "No se pudo enviar el código"))
                    }
                }
        }
    }

    fun verifyCode(code: String) {
        if (_state.value.isLoading) return
        if (code.trim().length != 6) {
            _state.update { it.copy(errorMessage = "El código debe tener 6 dígitos") }
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.verifyEmail(code)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { t ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = AuthErrorMessage.of(t, "Código inválido o expirado"))
                    }
                }
        }
    }

    /** "Verificar más tarde": el correo queda registrado sin verificar, se va al Home. */
    fun skip() = _state.update { it.copy(done = true) }
}

data class PrimerLoginUiState(
    val step: PrimerLoginStep = PrimerLoginStep.CONFIGURE,
    val userName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** En el paso de verificación: ya se envió el OTP. */
    val codeSent: Boolean = false,
    /** One-shot: cuenta configurada → ir al Home. */
    val done: Boolean = false,
)

enum class PrimerLoginStep { CONFIGURE, VERIFY_EMAIL }
