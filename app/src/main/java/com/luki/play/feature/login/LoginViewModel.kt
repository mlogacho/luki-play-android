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

/**
 * Modo de autenticación — mismos dos caminos que el front web
 * (`/auth/app/id-login` y `/auth/app/contract-login`).
 */
enum class LoginMode { CEDULA, CONTRATO }

data class LoginUiState(
    val mode: LoginMode = LoginMode.CEDULA,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedIn: Boolean = false,
)

/**
 * Login de abonado, réplica fiel del front web (`app/(auth)/login.tsx`):
 *
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

    fun setMode(mode: LoginMode) {
        _uiState.value = _uiState.value.copy(mode = mode, errorMessage = null)
    }

    fun login(credential: String, password: String) {
        val cred = credential.trim()
        if (cred.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = when (_uiState.value.mode) {
                    LoginMode.CEDULA   -> "La cédula es requerida"
                    LoginMode.CONTRATO -> "El número de contrato es requerido"
                }
            )
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "La contraseña es requerida")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = when (_uiState.value.mode) {
                LoginMode.CEDULA   -> authRepository.loginWithId(cred, password)
                LoginMode.CONTRATO -> authRepository.loginWithContract(cred, password)
            }
            result
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
