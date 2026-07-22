// data/streams/StreamSessionManager.kt
package com.luki.play.data.streams

import com.luki.play.data.auth.TokenStore
import com.luki.play.data.streams.api.StartStreamRequest
import com.luki.play.data.streams.api.StreamSessionApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ciclo de vida de la sesión de reproducción, réplica del que lleva el portal
 * en `app/(app)/player/[id].tsx` + `services/streamApi.ts`.
 *
 * Es lo que aplica el **tope de streams simultáneos del plan**: sin abrir
 * sesión se reproduce igual, pero el límite no cuenta. El camino nativo no la
 * abría, así que ver por la app no consumía cupo mientras que por web sí.
 *
 * Secuencia: `start` al empezar → `heartbeat` cada 20 s → `stop` al salir. Si
 * fallan [MAX_HEARTBEAT_FAILURES] heartbeats seguidos se intenta recuperar
 * (cerrar y reabrir el lease) en vez de dar la sesión por perdida, igual que
 * el portal.
 *
 * El refresco de token ante 401 NO se replica aquí: en Android ya lo hace el
 * `TokenAuthenticator` de OkHttp, que es el equivalente del `authedFetch` del
 * portal.
 */
@Singleton
class StreamSessionManager internal constructor(
    private val api: StreamSessionApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
    /** Scope del heartbeat; inyectable para poder avanzar el tiempo en tests. */
    private val scope: CoroutineScope,
) {

    @Inject
    constructor(api: StreamSessionApi, tokenStore: TokenStore) : this(
        api = api,
        tokenStore = tokenStore,
        ioDispatcher = Dispatchers.IO,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    /** Resultado de abrir sesión; el 429 se distingue porque tiene UI propia. */
    sealed interface OpenResult {
        data object Started : OpenResult
        /** Tope de streams simultáneos alcanzado (HTTP 429). */
        data object LimitReached : OpenResult
        data class Failed(val cause: Throwable) : OpenResult
    }

    private val _limitReached = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Se emite al chocar con el tope del plan. El portal navega al home con
     * `?streamLimit=1` y allí sale un aviso; aquí el reproductor se cierra y
     * el home escucha esto para mostrar el mismo mensaje.
     */
    val limitReached: SharedFlow<Unit> = _limitReached.asSharedFlow()

    private var streamId: String? = null
    private var heartbeatJob: Job? = null
    private var failures = 0

    /** Sesión vigente, para pruebas y diagnóstico. */
    val currentStreamId: String? get() = streamId

    /**
     * Abre la sesión y arranca el heartbeat.
     *
     * Idempotente por dispositivo: el backend hace upsert por
     * (cliente, deviceId), así que llamarlo de nuevo al cambiar de canal
     * actualiza el lease en vez de consumir otro cupo.
     */
    suspend fun open(channelId: String): OpenResult = withContext(ioDispatcher) {
        stopHeartbeat()
        try {
            val response = api.start(
                StartStreamRequest(channelId = channelId, deviceId = tokenStore.deviceId())
            )
            streamId = response.streamId
            failures = 0
            startHeartbeat(channelId)
            OpenResult.Started
        } catch (e: HttpException) {
            if (e.code() == HTTP_TOO_MANY_REQUESTS) {
                Timber.tag(TAG).w("tope de streams simultáneos alcanzado")
                _limitReached.tryEmit(Unit)
                OpenResult.LimitReached
            } else {
                Timber.tag(TAG).w(e, "no se pudo abrir la sesión de stream")
                OpenResult.Failed(e)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "no se pudo abrir la sesión de stream")
            OpenResult.Failed(e)
        }
    }

    /**
     * Cierra la sesión y libera el cupo.
     *
     * No es `suspend` a propósito: se llama desde `onDestroy`, donde no hay
     * dónde esperar. Si el proceso muere antes de que salga la petición, el
     * backend libera el lease igualmente al caducar el heartbeat.
     */
    fun close() {
        stopHeartbeat()
        val id = streamId ?: return
        streamId = null
        scope.launch {
            runCatching { api.stop(id) }
                .onFailure { Timber.tag(TAG).w(it, "no se pudo cerrar la sesión") }
        }
    }

    /**
     * Al volver a primer plano: heartbeat inmediato y, si el lease ya caducó
     * mientras la app estaba en background, se reabre.
     *
     * Volver de background NO cierra la sesión — el portal aprendió por las
     * malas que hacerlo provocaba cortes cuando el usuario solo apagaba la
     * pantalla o cambiaba de app un momento.
     */
    suspend fun onForeground(channelId: String) {
        val id = streamId
        if (id != null && sendHeartbeat(id)) {
            failures = 0
            return
        }
        Timber.tag(TAG).i("lease caducado en background; reabriendo")
        if (id != null) withContext(ioDispatcher) { runCatching { api.stop(id) } }
        open(channelId)
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    private fun startHeartbeat(channelId: String) {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val id = streamId ?: return@launch

                if (sendHeartbeat(id)) {
                    failures = 0
                    continue
                }

                failures += 1
                Timber.tag(TAG).w("heartbeat falló ($failures/$MAX_HEARTBEAT_FAILURES)")
                if (failures < MAX_HEARTBEAT_FAILURES) continue

                // Recuperación: cerrar el lease muerto y abrir uno nuevo.
                //
                // La reapertura va en OTRA corrutina a propósito. open()
                // empieza por stopHeartbeat(), que cancela `heartbeatJob`; si
                // se llamara aquí dentro se estaría cancelando a sí misma a
                // mitad y la reapertura moría con JobCancellationException,
                // dejando al usuario reproduciendo sin lease.
                Timber.tag(TAG).w("demasiados fallos; recuperando sesión")
                withContext(ioDispatcher) { runCatching { api.stop(id) } }
                scope.launch { open(channelId) }
                return@launch
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Un intento con un reintento, como el portal: un 4xx significa que la
     * sesión ya no vale y reintentar no arregla nada; un 5xx o un fallo de red
     * sí pueden ser transitorios.
     */
    private suspend fun sendHeartbeat(id: String): Boolean = withContext(ioDispatcher) {
        repeat(HEARTBEAT_ATTEMPTS) { attempt ->
            val outcome = runCatching { api.heartbeat(id) }
            val response = outcome.getOrNull()

            when {
                response != null && response.isSuccessful -> return@withContext true
                response != null && response.code() in 400..499 -> {
                    Timber.tag(TAG).w("heartbeat HTTP ${response.code()}: sesión inválida")
                    return@withContext false
                }
                else -> Timber.tag(TAG).w("heartbeat reintentable (intento ${attempt + 1})")
            }

            if (attempt < HEARTBEAT_ATTEMPTS - 1) delay(HEARTBEAT_RETRY_DELAY_MS)
        }
        false
    }

    private companion object {
        const val TAG = "StreamSession"
        const val HEARTBEAT_INTERVAL_MS = 20_000L
        const val HEARTBEAT_RETRY_DELAY_MS = 500L
        const val HEARTBEAT_ATTEMPTS = 2          // uno inicial + un reintento
        const val MAX_HEARTBEAT_FAILURES = 3
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
