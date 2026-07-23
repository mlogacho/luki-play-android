// feature/login/ActivateAccountViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthErrorMessage
import com.luki.play.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Pasos de la activación de cuenta, espejo de `activar.tsx` del portal. */
enum class ActivateStep { IDENTITY, REQUEST_CODE, VERIFY_CODE, CREATE_PASSWORD }

data class ActivateUiState(
    val step: ActivateStep = ActivateStep.IDENTITY,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** Correo registrado enmascarado, para el paso de verificación. */
    val maskedEmail: String? = null,
    /** One-shot: cuenta activada y sesión iniciada → navegar a Home. */
    val activated: Boolean = false,
)

/**
 * Activación de cuenta nueva — réplica del sub-flujo "Activar cuenta" de
 * `frontend/app/(auth)/activar.tsx`:
 *
 * 1. IDENTITY: cédula → `first-access` → customerId.
 * 2. REQUEST_CODE: envía el código al correo REGISTRADO (solo customerId; el
 *    backend elige el destino — así no se puede secuestrar una cuenta sin
 *    activar). Si el correo no se pudo enviar (`needsSupportCode`) NO se avanza.
 * 3. VERIFY_CODE: código de 6 → `verify-activation-code`.
 * 4. CREATE_PASSWORD: contraseña ([PasswordPolicy]) + correo opcional (solo
 *    notificaciones) → `activate` → tokens → sesión iniciada.
 */
@HiltViewModel
class ActivateAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivateUiState())
    val uiState: StateFlow<ActivateUiState> = _uiState.asStateFlow()

    private var customerId: String = ""
    private var verifiedCode: String = ""

    fun verifyIdentity(rawIdNumber: String) {
        val cedula = rawIdNumber.trim()
        if (cedula.isBlank()) {
            setError("La cédula es requerida")
            return
        }
        launchStep {
            authRepository.firstAccess(cedula)
                .onSuccess { id ->
                    customerId = id
                    _uiState.value = _uiState.value.copy(isLoading = false, step = ActivateStep.REQUEST_CODE)
                }
                .onFailure { fail(it, "No se pudo verificar la cédula") }
        }
    }

    fun requestCode() {
        if (customerId.isBlank()) { backToIdentity(); return }
        launchStep {
            authRepository.requestActivationCode(customerId)
                .onSuccess { channel ->
                    if (channel.needsSupportCode) {
                        // El correo no se pudo enviar: no llegó ningún código, así que
                        // no se avanza a teclearlo (si no, el usuario metería uno
                        // inexistente y vería "expirado").
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "No pudimos enviarte el código a tu correo. Contacta a soporte Luki para activar tu cuenta.",
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            step = ActivateStep.VERIFY_CODE,
                            maskedEmail = channel.maskedEmail,
                        )
                    }
                }
                .onFailure { fail(it, "Error al solicitar el código") }
        }
    }

    fun verifyCode(rawCode: String) {
        val code = rawCode.trim()
        if (code.length != 6) {
            setError("El código debe tener 6 caracteres")
            return
        }
        if (customerId.isBlank()) { backToIdentity(); return }
        launchStep {
            authRepository.verifyActivationCode(customerId, code)
                .onSuccess {
                    verifiedCode = code.uppercase()
                    _uiState.value = _uiState.value.copy(isLoading = false, step = ActivateStep.CREATE_PASSWORD)
                }
                .onFailure { fail(it, "Código inválido o expirado") }
        }
    }

    fun createPassword(newPassword: String, confirmPassword: String, email: String) {
        if (verifiedCode.length != 6) {
            setError("El código no fue verificado. Vuelve atrás.")
            return
        }
        PasswordPolicy.validate(newPassword)?.let { setError(it); return }
        if (newPassword != confirmPassword) {
            setError("Las contraseñas no coinciden")
            return
        }
        launchStep {
            authRepository.activate(customerId, verifiedCode, newPassword, email.trim())
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, activated = true) }
                .onFailure { fail(it, "No se pudo activar la cuenta") }
        }
    }

    fun backTo(step: ActivateStep) {
        _uiState.value = _uiState.value.copy(step = step, errorMessage = null, infoMessage = null)
    }

    fun backToIdentity() = backTo(ActivateStep.IDENTITY)

    private inline fun launchStep(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            block()
        }
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message, infoMessage = null)
    }

    private fun fail(t: Throwable, fallback: String) {
        Timber.tag(TAG).w(t, "activación: %s", fallback)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = AuthErrorMessage.of(t, fallback))
    }

    private companion object { const val TAG = "ActivateVM" }
}
