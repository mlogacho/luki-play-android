// data/auth/AuthRepository.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.ChangePasswordRequest
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import com.luki.play.data.auth.api.UserProfileDto
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
    private val tokenStore: TokenStore,
    /** Inyectable para que los tests JVM controlen la concurrencia. */
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(authApi: AuthApi, tokenStore: TokenStore) :
        this(authApi, tokenStore, Dispatchers.IO)

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

    /**
     * Carga el perfil del usuario autenticado. El Bearer lo adjunta el
     * interceptor de red; un 401 aquí se traduce arriba con [AuthErrorMessage].
     */
    suspend fun getProfile(): Result<UserProfile> = withContext(ioDispatcher) {
        runCatching { authApi.me().toDomain() }
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
                authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword))
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
