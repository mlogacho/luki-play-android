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

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(playerListener())
        p.addAnalyticsListener(qosListener)
    }

    /**
     * MediaSession para que el sistema muestre controles de transporte en el
     * lockscreen del móvil y en el carril de notificaciones de Android TV.
     * Se crea junto con el player y se libera en [release].
     */
    private val mediaSession: MediaSession = MediaSession.Builder(context, player)
        .setId("luki-play-session")
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

        player.apply {
            setMediaSource(mediaSource)
            seekTo(viewModel.restorePosition())
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
        player.play()
    }

    fun release() {
        Timber.tag(TAG).d("release")
        viewModel.saveCurrentPosition(player.currentPosition)
        player.removeAnalyticsListener(qosListener)
        mediaSession.release()
        player.release()
    }

    /** True si el player está cargado y reproduciendo / en buffering. */
    fun isPlayingOrBuffering(): Boolean =
        player.playbackState == Player.STATE_READY ||
        player.playbackState == Player.STATE_BUFFERING

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
                    viewModel.onPlaybackReady()
                    callback.onReady()
                }
                Player.STATE_ENDED -> callback.onEnded()
                Player.STATE_IDLE  -> { /* idle handled via onPlayerError */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = error.message ?: "Unknown playback error"
            Timber.tag(TAG).e(error, "onPlayerError: %s", msg)
            viewModel.onPlaybackError(msg)
            callback.onError(msg)
        }
    }
}
