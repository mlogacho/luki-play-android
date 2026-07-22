// feature/login/LoginViewModel.kt
package com.luki.play.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.auth.AuthRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
)

/**
 * Login de abonado, réplica fiel del front web (`app/(auth)/login.tsx`):
 *
 * - Solo cédula, igual que el portal. El endpoint de contrato existe en
 *   [AuthRepository] (espeja la API) pero la pantalla web tampoco lo
 *   expone, así que aquí no se ofrece.
 * - Validación local: SOLO trim + campo requerido. Deliberadamente sin
 *   checksum ni longitud de cédula — en la base conviven cédulas (10
 *   dígitos) y RUC (13), y el front tampoco valida formato.
 * - El deviceId lo aporta [AuthRepository] desde el TokenStore nativo
 *   (equivalente al `getOrCreateDeviceId()` de localStorage en la web).
 * - Errores: mensaje del backend si viene (`{ message }`), con los mismos
 *   fallbacks que la web ("Credenciales inválidas" / "Sin conexión…").
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(idNumber: String, password: String) {
        val cedula = idNumber.trim()
        // Mismo orden y mismos mensajes que handleLogin() del portal.
        if (cedula.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "La cédula es requerida")
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "La contraseña es requerida")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.loginWithId(cedula, password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
                }
                .onFailure { t ->
                    Timber.tag(TAG).w(t, "login falló")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = t.toUserMessage(),
                    )
                }
        }
    }

    /** El NavGraph ya navegó; evita re-disparos al recomponer. */
    fun consumeLoggedIn() {
        _uiState.value = _uiState.value.copy(loggedIn = false)
    }

    // ── Error mapping ────────────────────────────────────────────────────────

    @JsonClass(generateAdapter = true)
    internal data class ApiErrorDto(val message: String?)

    private val errorAdapter = Moshi.Builder().build().adapter(ApiErrorDto::class.java)

    /** Mismos mensajes que la web: message del backend → fallback genérico. */
    private fun Throwable.toUserMessage(): String = when (this) {
        is HttpException -> runCatching {
            response()?.errorBody()?.string()?.let { errorAdapter.fromJson(it)?.message }
        }.getOrNull() ?: "Credenciales inválidas"
        is IOException -> "Sin conexión. Verifica tu internet e intenta de nuevo."
        else -> "Credenciales inválidas"
    }

    private companion object { const val TAG = "LoginVM" }
}
