// player/qos/QoSAnalyticsListener.kt
package com.luki.play.player.qos

import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import timber.log.Timber

/**
 * Snapshot inmutable de las métricas QoS recolectadas hasta el momento.
 *
 * Diseñado para enviarse periódicamente a un backend de telemetría
 * (Firebase Analytics / Mux / Conviva) o exponerse en debug HUD.
 *
 * @property startupTimeMs   Tiempo desde `onLoadStarted` hasta el primer frame
 *                           renderizado. -1 si aún no se ha alcanzado READY.
 * @property rebufferCount   Veces que el estado pasó a BUFFERING tras estar READY.
 * @property rebufferMs      Tiempo total acumulado en estado BUFFERING tras el primer READY.
 * @property bitrateSwitches Número de cambios de calidad de vídeo.
 * @property currentBitrate  Último bitrate de vídeo reportado (bps), 0 si desconocido.
 * @property recoveredErrorCount Errores recuperados automáticamente por el
 *                           manager (p.ej. reenganche tras BEHIND_LIVE_WINDOW).
 * @property fatalErrorCode  Código del último error fatal; null si no hubo.
 */
data class QoSSnapshot(
    val streamUrl: String?,
    val startupTimeMs: Long,
    val rebufferCount: Int,
    val rebufferMs: Long,
    val bitrateSwitches: Int,
    val currentBitrate: Int,
    val recoveredErrorCount: Int,
    val fatalErrorCode: Int?,
)

/**
 * AnalyticsListener que mantiene métricas QoS clave para reproducción OTT.
 *
 * Diseño:
 *  - Estado mutable interno; expone snapshots inmutables vía [snapshot].
 *  - Sin dependencia de Firebase ni de Hilt: el caller decide cómo persistir.
 *  - Reset implícito en cada `onLoadStarted`: cada nueva fuente parte de cero.
 *  - [clock] inyectable (default [SystemClock.elapsedRealtime]) para que los
 *    unit tests JVM no dependan de APIs de Android ni de Thread.sleep.
 *
 * Eventos emitidos a Timber con tag "QoS":
 *  - `qos.startup`   (al llegar a READY por primera vez)
 *  - `qos.rebuffer`  (cada vez que sale de BUFFERING posterior al startup)
 *  - `qos.bitrate`   (cada cambio de bitrate de vídeo)
 *  - `qos.error`     (cada error fatal)
 */
@UnstableApi
class QoSAnalyticsListener(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AnalyticsListener {

    private var streamUrl: String? = null
    private var loadStartedAtMs: Long = -1
    private var firstReadyAtMs: Long = -1
    private var rebufferStartedAtMs: Long = -1
    private var rebufferCount = 0
    private var rebufferMs = 0L
    private var bitrateSwitches = 0
    private var currentBitrate = 0
    private var recoveredErrorCount = 0
    private var fatalErrorCode: Int? = null

    /**
     * Llamar desde el manager al iniciar la carga de un stream. Resetea el estado
     * porque [AnalyticsListener] no tiene un callback dedicado de "nueva fuente".
     */
    fun onLoadStarted(url: String) {
        streamUrl = url
        loadStartedAtMs = clock()
        firstReadyAtMs = -1
        rebufferStartedAtMs = -1
        rebufferCount = 0
        rebufferMs = 0
        bitrateSwitches = 0
        currentBitrate = 0
        recoveredErrorCount = 0
        fatalErrorCode = null
    }

    /**
     * El caller (manager) recuperó un error sin intervención del usuario
     * (p.ej. reenganche al live edge tras BEHIND_LIVE_WINDOW). Se cuenta
     * aparte y NO marca la sesión como fatal.
     */
    fun onErrorRecovered() {
        recoveredErrorCount++
        Timber.tag(TAG).i("qos.recovered count=%d", recoveredErrorCount)
    }

    /**
     * El caller (manager) decidió que un error es fatal (incluye el caso de
     * cap de reenganches agotado). Es el ÚNICO escritor de [fatalErrorCode]:
     * la clasificación recuperable/fatal vive solo en el manager, así no hay
     * dos listas de códigos que puedan divergir.
     */
    fun onFatalError(code: Int) {
        fatalErrorCode = code
    }

    fun snapshot(): QoSSnapshot = QoSSnapshot(
        streamUrl           = streamUrl,
        startupTimeMs       = if (firstReadyAtMs > 0 && loadStartedAtMs > 0) firstReadyAtMs - loadStartedAtMs else -1,
        rebufferCount       = rebufferCount,
        rebufferMs          = rebufferMs,
        bitrateSwitches     = bitrateSwitches,
        currentBitrate      = currentBitrate,
        recoveredErrorCount = recoveredErrorCount,
        fatalErrorCode      = fatalErrorCode,
    )

    // ── AnalyticsListener callbacks ──────────────────────────────────────────

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) {
        when (state) {
            Player.STATE_READY -> {
                if (firstReadyAtMs < 0 && loadStartedAtMs > 0) {
                    firstReadyAtMs = clock()
                    Timber.tag(TAG).i("qos.startup ms=%d url=%s", firstReadyAtMs - loadStartedAtMs, streamUrl)
                }
                if (rebufferStartedAtMs > 0) {
                    val delta = clock() - rebufferStartedAtMs
                    rebufferMs += delta
                    rebufferStartedAtMs = -1
                    Timber.tag(TAG).i("qos.rebuffer ms=%d total=%d count=%d", delta, rebufferMs, rebufferCount)
                }
            }
            Player.STATE_BUFFERING -> {
                if (firstReadyAtMs > 0 && rebufferStartedAtMs < 0) {
                    rebufferStartedAtMs = clock()
                    rebufferCount++
                }
            }
            Player.STATE_ENDED, Player.STATE_IDLE -> {
                // sin métrica adicional aquí
            }
        }
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
    ) {
        val newBitrate = format.bitrate.takeIf { it > 0 } ?: format.peakBitrate.takeIf { it > 0 } ?: 0
        if (newBitrate > 0 && newBitrate != currentBitrate) {
            currentBitrate = newBitrate
            bitrateSwitches++
            Timber.tag(TAG).d("qos.bitrate bps=%d switches=%d (%dx%d)", newBitrate, bitrateSwitches, format.width, format.height)
        }
    }

    /**
     * Solo log. La clasificación recuperable/fatal es del manager, que reporta
     * el desenlace vía [onErrorRecovered] / [onFatalError] — así el resultado
     * no depende del orden de notificación entre listeners ni existe una
     * segunda lista de códigos recuperables que mantener sincronizada.
     */
    override fun onPlayerError(
        eventTime: AnalyticsListener.EventTime,
        error: PlaybackException,
    ) {
        Timber.tag(TAG).e(error, "qos.error code=%d name=%s", error.errorCode, error.errorCodeName)
    }

    companion object {
        private const val TAG = "QoS"
    }
}
