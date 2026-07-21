// data/auth/AuthRepository.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
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
                )
                _session.value = SessionState.Authenticated(session.userId, session.displayName)
                session
            }.onFailure { Timber.w(it, "AuthRepository: login falló") }
        }

    private fun currentSnapshot(): SessionState {
        val token = tokenStore.accessToken() ?: return SessionState.Anonymous
        if (token.isBlank()) return SessionState.Anonymous
        return SessionState.Authenticated(
            userId      = tokenStore.userId().orEmpty(),
            displayName = tokenStore.displayName().orEmpty(),
        )
    }
}
