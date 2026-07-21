// player/LukiPlayerManager.kt
package com.luki.play.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
         * Reenganches consecutivos permitidos ante ERROR_CODE_BEHIND_LIVE_WINDOW
         * sin alcanzar READY entre medio. Superado el cap, el error se trata
         * como fatal y llega a la UI — evita un bucle infinito de seek+prepare
         * si la condición recurre (playlist congelado, reloj desviado).
         */
        private const val MAX_BEHIND_LIVE_RECOVERIES = 3

        /**
         * Offset máximo respecto al live edge tolerado al reanudar. Por debajo
         * se reanuda donde estaba (interrupciones breves no pagan salto ni
         * rebuffer); por encima se reengancha al vivo. Debe superar el offset
         * normal de reproducción (hold-back ≈ 3 × targetDuration ≈ 18 s con
         * segmentos de 6 s).
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

    /** Reenganches BEHIND_LIVE_WINDOW consecutivos sin READY entre medio. */
    private var behindLiveRecoveries = 0

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
        viewModel.saveCurrentPosition(player.currentPosition)
        player.pause()
    }

    fun resume() {
        // Un lineal pausado queda cada vez más atrás del vivo. Solo se
        // reengancha al live edge si quedó materialmente atrás (pausa larga);
        // una interrupción breve (diálogo del sistema, multiventana) reanuda
        // donde estaba, sin salto ni rebuffer. Antes del primer READY es un
        // no-op (offset desconocido / item no live).
        if (player.isCurrentMediaItemLive) {
            val offsetMs = player.currentLiveOffset
            if (offsetMs != C.TIME_UNSET && offsetMs > MAX_RESUME_LIVE_OFFSET_MS) {
                player.seekToDefaultPosition()
            }
        }
        player.play()
    }

    fun release() {
        Timber.tag(TAG).d("release")
        viewModel.saveCurrentPosition(player.currentPosition)
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
                    behindLiveRecoveries = 0
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
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                behindLiveRecoveries < MAX_BEHIND_LIVE_RECOVERIES
            ) {
                // Recuperable: la posición cayó fuera de la ventana DVR del
                // playlist (pausa larga, red lenta). Reengancha al vivo y sigue
                // sin mostrar pantalla de error. El contador evita un bucle
                // infinito si la condición recurre; se resetea en cada READY.
                behindLiveRecoveries++
                Timber.tag(TAG).w(
                    "onPlayerError: behind live window, reenganchando (%d/%d)",
                    behindLiveRecoveries, MAX_BEHIND_LIVE_RECOVERIES,
                )
                qosListener.onErrorRecovered()
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            val msg = error.message ?: "Unknown playback error"
            Timber.tag(TAG).e(error, "onPlayerError: %s", msg)
            qosListener.onFatalError(error.errorCode)
            viewModel.onPlaybackError(msg)
            callback.onError(msg)
        }
    }
}
