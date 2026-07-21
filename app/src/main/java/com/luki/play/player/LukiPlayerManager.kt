// player/LukiPlayerManager.kt
package com.luki.play.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import com.luki.play.player.drm.WidevineProvider
import com.luki.play.player.qos.QoSAnalyticsListener
import timber.log.Timber
import java.util.UUID

/**
 * Manages the [ExoPlayer] lifecycle for [PlayerActivity].
 *
 * Capacidades:
 *  - Selección de `MediaSource` según [ManifestType] (HLS, DASH, fallback genérico).
 *  - Activación de Widevine cuando [StreamConfig.hasDrm] es `true`.
 *  - Persistencia y restauración de posición vía [PlayerViewModel].
 *  - Métricas QoS (startup, rebuffer, bitrate, errores) vía [QoSAnalyticsListener].
 *
 * @param context   Application context — usado para construir ExoPlayer y data sources.
 * @param viewModel ViewModel de la Activity; recibe transiciones de estado.
 * @param callback  Callback liviano para la Activity (UI).
 */
@UnstableApi
class LukiPlayerManager(
    private val context: Context,
    private val viewModel: PlayerViewModel,
    private val callback: PlayerCallback,
) {

    companion object {
        private const val TAG = "LukiPlayerManager"

        /**
         * Reenganches permitidos ante ERROR_CODE_BEHIND_LIVE_WINDOW dentro de
         * una misma ráfaga (ver [BLW_BURST_WINDOW_MS]). Superado el cap, el
         * error se trata como fatal y llega a la UI — evita un bucle infinito
         * de seek+prepare si la condición recurre (playlist congelado, reloj
         * desviado). El contador NO se resetea al llegar a READY: un playlist
         * congelado alcanza READY entre errores y reventaría el cap si sí.
         */
        private const val MAX_BEHIND_LIVE_RECOVERIES = 3

        /**
         * Dos BLW separados por más de esto se consideran ráfagas distintas y
         * el contador arranca de cero: un BLW esporádico (una pausa larga cada
         * tanto) debe recuperarse siempre; solo la recurrencia rápida es fatal.
         */
        private const val BLW_BURST_WINDOW_MS = 60_000L

        /**
         * Tope del umbral de reenganche al reanudar. El umbral efectivo es
         * min(esto, mitad de la distancia al inicio de la ventana DVR): con la
         * ventana real de hoy (36 s, default position ≈ 18 s) un tope fijo de
         * 30 s sería inalcanzable porque BEHIND_LIVE_WINDOW dispara antes.
         */
        private const val MAX_RESUME_LIVE_OFFSET_MS = 30_000L
    }

    interface PlayerCallback {
        fun onReady()
        fun onBuffering()
        fun onEnded()
        fun onError(message: String)
    }

    // ── Data sources compartidos ─────────────────────────────────────────────

    private val httpDataSourceFactory: DataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent("LukiPlay-Android")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

    private val widevineProvider = WidevineProvider(httpDataSourceFactory)
    private val qosListener = QoSAnalyticsListener()

    // ── Estado de reproducción ──────────────────────────────────────────────

    /**
     * Posición a restaurar en el primer READY. El seek se difiere porque en
     * un vivo cualquier seek absoluto apunta dentro de la ventana DVR y deja
     * al usuario detrás del live edge; recién con el timeline cargado el
     * player sabe si el item es live (solo VOD restaura).
     */
    private var pendingRestorePositionMs = 0L

    /** Reenganches BEHIND_LIVE_WINDOW dentro de la ráfaga actual. */
    private var behindLiveRecoveries = 0

    /** Instante ([SystemClock.elapsedRealtime]) del último reenganche BLW. */
    private var lastBlwRecoveryAtMs = 0L

    /**
     * Hay un reenganche BLW en vuelo cuyo desenlace aún no se conoce. Se
     * confirma como recuperación (QoS) recién al llegar a READY — así el
     * contador de QoS mide recuperaciones reales, no intentos.
     */
    private var pendingBlwRecovery = false

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(playerListener())
        p.addAnalyticsListener(qosListener)
    }

    /**
     * MediaSession que expone el player al sistema (audio focus, rutas, Cast).
     * Nota: sin un MediaSessionService con notificación propia NO aparecen
     * controles en lockscreen; si se necesitan, va como trabajo aparte.
     *
     * El ID es único por instancia (UUID, no timestamp: un timestamp en ms
     * puede colisionar): Media3 lanza IllegalStateException si dos sesiones
     * del proceso comparten ID, y en el race stopStream→playStream la
     * Activity saliente puede solaparse brevemente con la entrante.
     */
    private val mediaSession: MediaSession = MediaSession.Builder(context, player)
        .setId("luki-play-session-${UUID.randomUUID()}")
        .build()

    // ── Public API ───────────────────────────────────────────────────────────

    fun load(config: StreamConfig) {
        Timber.tag(TAG).d(
            "load: %s | type=%s | drm=%s | subtitle=%s",
            config.url,
            config.effectiveManifestType(),
            config.drmScheme,
            config.subtitleUri,
        )

        qosListener.onLoadStarted(config.url)

        val mediaItem = MediaItem.Builder()
            .setUri(config.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(config.title.ifBlank { "Luki Play" })
                    .build()
            )
            .apply {
                if (!config.subtitleUri.isNullOrBlank()) {
                    val subtitle = MediaItem.SubtitleConfiguration.Builder(
                        android.net.Uri.parse(config.subtitleUri)
                    )
                        .setMimeType(config.subtitleMimeType)
                        .setLanguage("es")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    setSubtitleConfigurations(listOf(subtitle))
                }
            }
            .build()

        val mediaSource = buildMediaSource(mediaItem, config)

        pendingRestorePositionMs = viewModel.restorePosition()
        behindLiveRecoveries = 0
        lastBlwRecoveryAtMs = 0L
        pendingBlwRecovery = false

        player.apply {
            setMediaSource(mediaSource)
            // Sin seek aquí: ExoPlayer arranca en la posición por defecto
            // (live edge − hold-back para vivos, 0 para VOD). La restauración
            // de posición se difiere al primer READY — ver pendingRestorePositionMs.
            playWhenReady = true
            prepare()
        }

        viewModel.setStreamUrl(config.url)
    }

    fun saveAndPause() {
        // Antes del primer READY el seek diferido aún no corrió y
        // currentPosition es 0: guardar aquí pisaría con 0 la posición
        // pendiente de restaurar (Home antes de que cargue el manifiesto).
        if (pendingRestorePositionMs == 0L) {
            viewModel.saveCurrentPosition(player.currentPosition)
        }
        player.pause()
    }

    fun resume() {
        // Un lineal pausado queda cada vez más atrás del vivo. Solo se
        // reengancha al live edge si quedó materialmente atrás (pausa larga);
        // una interrupción breve (diálogo del sistema, multiventana) reanuda
        // donde estaba, sin salto ni rebuffer. Antes del primer READY es un
        // no-op (timeline vacío / item no live).
        //
        // La distancia se mide contra el timeline (defaultPosition − posición),
        // NUNCA contra currentLiveOffset: en HLS ese offset se calcula con el
        // reloj del dispositivo contra el PROGRAM-DATE-TIME del servidor, y un
        // reloj desviado lo vuelve inútil en ambas direcciones (adelantado:
        // reengancha y rebufferea en cada resume; atrasado: nunca reengancha).
        val timeline = player.currentTimeline
        if (player.isCurrentMediaItemLive && !timeline.isEmpty) {
            val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            val defaultPositionMs = window.defaultPositionMs
            if (defaultPositionMs > 0) {
                val behindLiveMs = defaultPositionMs - player.currentPosition
                val thresholdMs = minOf(MAX_RESUME_LIVE_OFFSET_MS, defaultPositionMs / 2)
                if (behindLiveMs > thresholdMs) player.seekToDefaultPosition()
            }
        }
        player.play()
    }

    fun release() {
        Timber.tag(TAG).d("release")
        // Deja rastro de la sesión en el log (Crashlytics en release vía
        // CrashlyticsTree) — sin esto las métricas QoS no salen del proceso.
        Timber.tag(TAG).i("qos.final %s", qosListener.snapshot())
        if (pendingRestorePositionMs == 0L) {
            viewModel.saveCurrentPosition(player.currentPosition)
        }
        player.removeAnalyticsListener(qosListener)
        mediaSession.release()
        player.release()
    }

    /** Expone las métricas QoS actuales para debug o telemetría externa. */
    fun qosSnapshot() = qosListener.snapshot()

    // ── MediaSource selection ───────────────────────────────────────────────

    private fun buildMediaSource(mediaItem: MediaItem, config: StreamConfig): MediaSource {
        val drmProvider = widevineProvider.provider(config)

        return when (config.effectiveManifestType()) {
            ManifestType.HLS -> HlsMediaSource.Factory(httpDataSourceFactory)
                .setDrmSessionManagerProvider(drmProvider)
                .createMediaSource(mediaItem)

            ManifestType.DASH -> DashMediaSource.Factory(httpDataSourceFactory)
                .setDrmSessionManagerProvider(drmProvider)
                .createMediaSource(mediaItem)

            ManifestType.OTHER -> DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpDataSourceFactory)
                .setDrmSessionManagerProvider(drmProvider)
                .createMediaSource(mediaItem)
        }
    }

    // ── Player.Listener ─────────────────────────────────────────────────────

    private fun playerListener() = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            Timber.tag(TAG).d("playbackState=%d", state)
            when (state) {
                Player.STATE_BUFFERING -> {
                    viewModel.setStreamUrl(
                        player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                    )
                    callback.onBuffering()
                }
                Player.STATE_READY -> {
                    if (pendingBlwRecovery) {
                        // Reenganche BLW confirmado: recién aquí cuenta como
                        // recuperación real. El contador de ráfaga NO se
                        // resetea (eso lo decide BLW_BURST_WINDOW_MS).
                        pendingBlwRecovery = false
                        qosListener.onErrorRecovered()
                    }
                    val pending = pendingRestorePositionMs
                    pendingRestorePositionMs = 0L
                    if (pending > 0 && !player.isCurrentMediaItemLive) {
                        player.seekTo(pending)
                    }
                    viewModel.onPlaybackReady()
                    callback.onReady()
                }
                Player.STATE_ENDED -> callback.onEnded()
                Player.STATE_IDLE  -> { /* idle handled via onPlayerError */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastBlwRecoveryAtMs > BLW_BURST_WINDOW_MS) {
                    // Ráfaga nueva: el BLW anterior quedó lejos; un esporádico
                    // (pausa larga ocasional) debe recuperarse siempre.
                    behindLiveRecoveries = 0
                }
                if (behindLiveRecoveries < MAX_BEHIND_LIVE_RECOVERIES) {
                    // Recuperable: la posición cayó fuera de la ventana DVR del
                    // playlist (pausa larga, red lenta). Reengancha al vivo y
                    // sigue sin mostrar pantalla de error. El cap por ráfaga
                    // corta el bucle si la condición recurre en caliente.
                    behindLiveRecoveries++
                    lastBlwRecoveryAtMs = nowMs
                    pendingBlwRecovery = true
                    Timber.tag(TAG).w(
                        "onPlayerError: behind live window, reenganchando (%d/%d)",
                        behindLiveRecoveries, MAX_BEHIND_LIVE_RECOVERIES,
                    )
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
            }
            val msg = error.message ?: "Unknown playback error"
            Timber.tag(TAG).e(error, "onPlayerError: %s", msg)
            qosListener.onFatalError(error.errorCode)
            Timber.tag(TAG).i("qos.final %s", qosListener.snapshot())
            viewModel.onPlaybackError(msg)
            callback.onError(msg)
        }
    }
}
