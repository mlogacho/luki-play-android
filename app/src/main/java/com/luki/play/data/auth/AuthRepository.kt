// data/auth/AuthRepository.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AccountApi
import com.luki.play.data.auth.api.ActivateRequest
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.ChangePasswordRequest
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.FirstAccessRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.RequestActivationCodeRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import com.luki.play.data.auth.api.UserProfileDto
import com.luki.play.data.auth.api.VerifyActivationCodeRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modelo de dominio devuelto al UI tras una operación de auth.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val displayName: String,
)

/**
 * Estado de sesión observable por el UI / WebView.
 */
sealed interface SessionState {
    data object Anonymous : SessionState
    data class Authenticated(val userId: String, val displayName: String) : SessionState
}

/**
 * Resultado de solicitar el código de activación. `sent` = si se envió;
 * `needsSupportCode` = el correo no se pudo enviar (ir a soporte, no teclear);
 * `maskedEmail` = correo registrado enmascarado, para mostrarlo en el paso.
 */
data class ActivationChannel(
    val sent: Boolean,
    val needsSupportCode: Boolean,
    val maskedEmail: String?,
)

/**
 * Perfil del usuario autenticado, ya mapeado a dominio (`GET /auth/me`).
 * Réplica de los campos que consume la pantalla de perfil del portal.
 */
data class UserProfile(
    val id: String,
    val firstName: String,
    val lastName: String,
    val idNumber: String?,
    val contractNumber: String?,
    val email: String,
    val serviceStatus: String?,
    val canAccessOtt: Boolean,
    val lastLoginAt: String?,
) {
    /** Nombre completo, o cadena vacía si el backend no lo trae. */
    val fullName: String get() = "$firstName $lastName".trim()
}

/**
 * Repositorio de autenticación: encapsula login (id/contrato), refresh y logout,
 * y persiste la sesión a través de [TokenStore].
 *
 * Las llamadas suspenden y se ejecutan en [Dispatchers.IO]; el callsite puede
 * invocarlas desde el hilo principal sin preocuparse por el dispatcher.
 */
@Singleton
class AuthRepository internal constructor(
    private val authApi: AuthApi,
    private val accountApi: AccountApi,
    private val tokenStore: TokenStore,
    /** Inyectable para que los tests JVM controlen la concurrencia. */
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(authApi: AuthApi, accountApi: AccountApi, tokenStore: TokenStore) :
        this(authApi, accountApi, tokenStore, Dispatchers.IO)

    private val _session = MutableStateFlow(currentSnapshot())

    /** Estado de sesión observable. Emite cada vez que cambia el TokenStore. */
    val session: Flow<SessionState> = _session.asStateFlow()

    /** Lectura síncrona del estado actual. */
    fun current(): SessionState = _session.value

    suspend fun loginWithId(idNumber: String, password: String): Result<AuthSession> =
        runAuth {
            authApi.loginWithId(
                IdLoginRequest(
                    idNumber  = idNumber,
                    password  = password,
                    deviceId  = tokenStore.deviceId(),
                )
            )
        }

    suspend fun loginWithContract(contractNumber: String, password: String): Result<AuthSession> =
        runAuth {
            authApi.loginWithContract(
                ContractLoginRequest(
                    contractNumber = contractNumber,
                    password       = password,
                    deviceId       = tokenStore.deviceId(),
                )
            )
        }

    /**
     * Pide el OTP de recuperación al correo registrado. El backend responde
     * siempre igual (anti-enumeración): un éxito aquí NO confirma que la
     * cédula exista.
     */
    suspend fun requestPasswordOtp(idNumber: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            authApi.requestPasswordOtp(RequestPasswordOtpRequest(idNumber))
            Unit
        }.onFailure { Timber.w(it, "AuthRepository: requestPasswordOtp falló") }
    }

    /** Restablece la contraseña con el OTP recibido por correo. */
    suspend fun resetPasswordWithOtp(
        idNumber: String,
        otpCode: String,
        newPassword: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            authApi.resetPasswordWithOtp(ResetPasswordOtpRequest(idNumber, otpCode, newPassword))
            Unit
        }.onFailure { Timber.w(it, "AuthRepository: resetPasswordWithOtp falló") }
    }

    // ── Activación de cuenta ────────────────────────────────────────────────

    /** Paso 1: identifica al cliente por cédula → customerId. */
    suspend fun firstAccess(idNumber: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            authApi.firstAccess(FirstAccessRequest(idNumber)).customerId
                ?.takeIf { it.isNotBlank() }
                ?: error("first-access no devolvió customerId")
        }.onFailure { Timber.w(it, "AuthRepository: firstAccess falló") }
    }

    /**
     * Paso 2: pide el código al correo REGISTRADO (solo se envía el customerId;
     * el backend elige el destino). Devuelve si se envió y el correo enmascarado.
     */
    suspend fun requestActivationCode(customerId: String): Result<ActivationChannel> =
        withContext(ioDispatcher) {
            runCatching {
                val dto = authApi.requestActivationCode(RequestActivationCodeRequest(customerId))
                ActivationChannel(dto.sent, dto.needsSupportCode, dto.maskedEmail)
            }.onFailure { Timber.w(it, "AuthRepository: requestActivationCode falló") }
        }

    /** Paso 3: verifica el código de 6 caracteres. */
    suspend fun verifyActivationCode(customerId: String, code: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                authApi.verifyActivationCode(VerifyActivationCodeRequest(customerId, code.uppercase()))
                Unit
            }.onFailure { Timber.w(it, "AuthRepository: verifyActivationCode falló") }
        }

    /**
     * Paso 4: crea la contraseña y deja la sesión iniciada. `email` es opcional
     * (notificaciones), no el destino del código. Reutiliza [runAuth] para
     * persistir tokens y marcar la sesión como autenticada, igual que el login.
     */
    suspend fun activate(
        customerId: String,
        otpCode: String,
        password: String,
        email: String?,
    ): Result<AuthSession> = runAuth {
        authApi.activate(ActivateRequest(customerId, otpCode.uppercase(), password, email?.takeIf { it.isNotBlank() }))
    }

    /**
     * Solicitud de acceso de un no-cliente (captación de lead, sin auth). El
     * backend valida cédula/RUC y correo, y rechaza (409) si la cédula ya es
     * cliente o ya tiene una solicitud pendiente; ese mensaje sube tal cual con
     * [AuthErrorMessage]. `email`/`direccion` van solo si no están en blanco.
     */
    suspend fun submitRegistrationRequest(
        nombres: String,
        apellidos: String,
        idNumber: String,
        telefono: String,
        email: String?,
        direccion: String?,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            authApi.submitRegistrationRequest(
                com.luki.play.data.auth.api.RegistrationRequestBody(
                    nombres   = nombres.trim(),
                    apellidos = apellidos.trim(),
                    idNumber  = idNumber.trim(),
                    telefono  = telefono.trim(),
                    email     = email?.trim()?.takeIf { it.isNotBlank() },
                    direccion = direccion?.trim()?.takeIf { it.isNotBlank() },
                )
            )
            Unit
        }.onFailure { Timber.w(it, "AuthRepository: submitRegistrationRequest falló") }
    }

    /**
     * Carga el perfil del usuario autenticado. El Bearer lo adjunta el
     * interceptor de red; un 401 aquí se traduce arriba con [AuthErrorMessage].
     */
    suspend fun getProfile(): Result<UserProfile> = withContext(ioDispatcher) {
        runCatching { accountApi.me().toDomain() }
            .onFailure { Timber.w(it, "AuthRepository: getProfile falló") }
    }

    /**
     * Cambia la contraseña del usuario. En éxito el backend revoca TODAS las
     * sesiones (incluida la actual), así que el llamador debe cerrar sesión
     * localmente después — igual que el portal.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                accountApi.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                Unit
            }.onFailure { Timber.w(it, "AuthRepository: changePassword falló") }
        }

    suspend fun logout(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            // Best-effort: si el servidor no responde, igualmente limpiamos local.
            runCatching { authApi.logout() }
                .onFailure { Timber.w(it, "AuthRepository: logout remoto falló — limpiando local") }
            tokenStore.clear()
            _session.value = SessionState.Anonymous
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun runAuth(block: suspend () -> com.luki.play.data.auth.api.AuthResponseDto): Result<AuthSession> =
        withContext(ioDispatcher) {
            runCatching {
                val dto = block()
                val session = AuthSession(
                    accessToken  = dto.accessToken,
                    refreshToken = dto.refreshToken,
                    userId       = dto.resolvedUserId(),
                    displayName  = dto.resolvedDisplayName(),
                )
                tokenStore.save(
                    accessToken  = session.accessToken,
                    refreshToken = session.refreshToken,
                    userId       = session.userId.takeIf { it.isNotBlank() },
                    displayName  = session.displayName.takeIf { it.isNotBlank() },
                    email        = dto.user?.email,
                    plan         = dto.user?.plan,
                )
                _session.value = SessionState.Authenticated(session.userId, session.displayName)
                session
            }.onFailure { Timber.w(it, "AuthRepository: login falló") }
        }

    /** Mapea el DTO de `/auth/me` a dominio, con vacíos seguros. */
    private fun UserProfileDto.toDomain(): UserProfile = UserProfile(
        id             = id.orEmpty(),
        firstName      = firstName.orEmpty(),
        lastName       = lastName.orEmpty(),
        idNumber       = idNumber,
        contractNumber = contractNumber,
        email          = email.orEmpty(),
        serviceStatus  = serviceStatus,
        canAccessOtt   = canAccessOtt,
        lastLoginAt    = lastLoginAt,
    )

    private fun currentSnapshot(): SessionState {
        val token = tokenStore.accessToken() ?: return SessionState.Anonymous
        if (token.isBlank()) return SessionState.Anonymous
        return SessionState.Authenticated(
            userId      = tokenStore.userId().orEmpty(),
            displayName = tokenStore.displayName().orEmpty(),
        )
    }
}
