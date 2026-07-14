// data/network/TokenAuthenticator.kt
package com.luki.play.data.network

import com.luki.play.data.auth.TokenStore
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] que se dispara ante un `401 Unauthorized`.
 *
 * Estrategia:
 *  1. Lee el refresh token del [TokenStore]. Si no existe, no reintenta.
 *  2. Llama a `/auth/refresh` con un cliente sin este authenticator
 *     (inyectado vía [Provider] para romper el ciclo Hilt → OkHttp → Auth → Hilt).
 *  3. Persiste los nuevos tokens y reintenta la request original con el header
 *     `Authorization` actualizado.
 *  4. Si el servidor rechaza el refresh token (400/401/403), limpia la sesión
 *     y devuelve `null`. Ante fallos transitorios (sin red, 5xx, timeout) la
 *     sesión se conserva: el 401 pasa al caller pero el próximo intento con
 *     conectividad podrá refrescar.
 *
 * Sincronizado por instancia: dos 401 simultáneos solo provocan un refresh.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApiProvider: Provider<AuthApi>,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Evitar bucles: si ya intentamos refresh en esta cadena, abortar.
        if (responseCount(response) >= 2) return null

        val currentRefresh = tokenStore.refreshToken() ?: return null
        val priorAccess = authHeader(response.request)

        return synchronized(lock) {
            // Otro hilo pudo haber refrescado mientras esperábamos el lock
            val newest = tokenStore.accessToken()
            if (!newest.isNullOrBlank() && "Bearer $newest" != priorAccess) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $newest")
                    .build()
            }

            val refreshed = try {
                runBlocking {
                    refreshApiProvider.get().refresh(RefreshRequest(currentRefresh))
                }
            } catch (e: HttpException) {
                if (e.code() in REFRESH_REJECTED_CODES) {
                    Timber.w("TokenAuthenticator: refresh rechazado (HTTP %d) — limpiando sesión", e.code())
                    tokenStore.clear()
                } else {
                    Timber.w("TokenAuthenticator: refresh falló (HTTP %d) — se conserva la sesión", e.code())
                }
                return@synchronized null
            } catch (t: Throwable) {
                Timber.w(t, "TokenAuthenticator: refresh falló (transitorio) — se conserva la sesión")
                return@synchronized null
            }

            tokenStore.updateTokens(refreshed.accessToken, refreshed.refreshToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        }
    }

    private fun authHeader(req: Request): String? = req.header("Authorization")

    companion object {
        /**
         * Códigos con los que el backend rechaza un refresh token inválido o
         * expirado (400 = invalid_grant estilo OAuth2). Solo estos destruyen
         * la sesión local.
         */
        private val REFRESH_REJECTED_CODES = setOf(400, 401, 403)
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
