// data/auth/TvAuthRepository.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.TvActivateRequest
import com.luki.play.data.auth.api.TvAuthApi
import com.luki.play.data.auth.api.TvSessionRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Sesión de emparejamiento que la TV muestra como código + QR. */
data class TvPairingSession(
    val sessionId: String,
    val code: String,
    val activationUrl: String,
    val expiresInSeconds: Int,
)

/** Resultado de un sondeo del emparejamiento. */
sealed interface TvPollResult {
    data object Pending : TvPollResult
    data object Expired : TvPollResult
    /** El teléfono activó la sesión; los tokens ya quedaron persistidos. */
    data object Authenticated : TvPollResult
}

/**
 * Emparejamiento de TV (device-code), réplica de `tv-activation.tsx` +
 * `tvAuthApi.ts`. La TV crea una sesión y sondea; cuando el teléfono la activa
 * (vía `activar.tsx`), el poll trae tokens y aquí se persisten en el
 * [TokenStore] con el deviceId canónico.
 *
 * No actualiza el `SessionState` de [AuthRepository]: en TV el arranque decide
 * activación-vs-home leyendo el token del store, y la pantalla navega sola al
 * autenticarse. El interceptor de red usa el store directamente.
 */
@Singleton
class TvAuthRepository internal constructor(
    private val api: TvAuthApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(api: TvAuthApi, tokenStore: TokenStore) :
        this(api, tokenStore, Dispatchers.IO)

    suspend fun createSession(): Result<TvPairingSession> = withContext(ioDispatcher) {
        runCatching {
            val dto = api.createSession(TvSessionRequest(tvDeviceId = tokenStore.deviceId()))
            TvPairingSession(
                sessionId = dto.sessionId ?: error("tv/session sin sessionId"),
                code = dto.code.orEmpty(),
                activationUrl = dto.activationUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_ACTIVATION_URL,
                expiresInSeconds = dto.expiresInSeconds.takeIf { it > 0 } ?: DEFAULT_EXPIRY_SECONDS,
            )
        }.onFailure { Timber.w(it, "TvAuthRepository: createSession falló") }
    }

    suspend fun poll(sessionId: String): Result<TvPollResult> = withContext(ioDispatcher) {
        runCatching {
            val dto = api.poll(sessionId)
            when (dto.status) {
                "authenticated" -> {
                    val token = dto.accessToken
                    if (token.isNullOrBlank()) {
                        TvPollResult.Pending
                    } else {
                        tokenStore.save(
                            accessToken = token,
                            refreshToken = dto.refreshToken,
                            userId = dto.user?.id?.takeIf { it.isNotBlank() },
                            displayName = dto.user?.name?.takeIf { it.isNotBlank() },
                            email = dto.user?.email,
                            plan = dto.user?.plan,
                        )
                        TvPollResult.Authenticated
                    }
                }
                "expired" -> TvPollResult.Expired
                else -> TvPollResult.Pending
            }
        }.onFailure { Timber.w(it, "TvAuthRepository: poll falló") }
    }

    /**
     * Lado TELÉFONO: conecta el TV con el código que muestra. El backend hace el
     * login con estas credenciales usando el deviceId del TV, así que aquí NO se
     * persiste sesión alguna (la del teléfono no cambia).
     */
    suspend fun activateTv(
        code: String,
        idNumber: String,
        password: String,
    ): Result<TvActivateOutcome> = withContext(ioDispatcher) {
        runCatching {
            val dto = api.activateTv(
                TvActivateRequest(
                    code = code.trim().uppercase(),
                    idNumber = idNumber.trim(),
                    password = password,
                )
            )
            if (dto.requiresActivation) TvActivateOutcome.NeedsAccountActivation
            else TvActivateOutcome.Connected
        }.onFailure { Timber.w(it, "TvAuthRepository: activateTv falló") }
    }

    private companion object {
        const val DEFAULT_ACTIVATION_URL = "https://lukiplay.com/activar"
        const val DEFAULT_EXPIRY_SECONDS = 300
    }
}

/** Resultado de conectar un TV desde el teléfono. */
sealed interface TvActivateOutcome {
    /** TV conectado. */
    data object Connected : TvActivateOutcome
    /** La cuenta aún tiene clave temporal: primero hay que activarla. */
    data object NeedsAccountActivation : TvActivateOutcome
}
