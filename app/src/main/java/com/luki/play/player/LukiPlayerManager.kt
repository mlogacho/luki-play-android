// player/LukiPlayerManager.kt
package com.luki.play.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages the [ExoPlayer] lifecycle for [PlayerActivity].
 *
 * Wraps ExoPlayer with:
 *  - A [PlayerCallback] for clean Activity/Fragment communication.
 *  - Position save/restore via [PlayerViewModel.saveCurrentPosition] /
 *    [PlayerViewModel.restorePosition].
 *  - DRM stub: reads [StreamConfig.drmToken] but does NOT activate Widevine
 *    in this version (reserved for future implementation).
 *
 * @param context   Application context — stored for ExoPlayer construction only.
 * @param viewModel The activity's ViewModel; state transitions are posted here.
 * @param callback  Lightweight event callback for the Activity.
 */
class LukiPlayerManager(
    private val context: Context,
    private val viewModel: PlayerViewModel,
    private val callback: PlayerCallback
) {

    companion object {
        private const val TAG = "LukiPlayerManager"
    }

    // ── Interface ────────────────────────────────────────────────────────────

    interface PlayerCallback {
        fun onReady()
        fun onBuffering()
        fun onEnded()
        fun onError(message: String)
    }

    // ── ExoPlayer ────────────────────────────────────────────────────────────

    /** Direct access for binding to [androidx.media3.ui.PlayerView]. */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(playerListener())
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Load [config] into the player and start playback, restoring the
     * last persisted position if one exists.
     *
     * Subtítulos: si [StreamConfig.subtitleUri] no es null, se adjunta
     * como pista de subtítulos secundaria (WebVTT / SRT / TTML).
     *
     * ⚠️ DRM: drmToken es ignorado en esta versión.
     */
    fun load(config: StreamConfig) {
        Log.d(TAG, "load: ${config.url} | subtitle: ${config.subtitleUri}")

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(config.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(config.title.ifBlank { "Luki Play" })
                    .build()
            )

        // ── Subtítulos opcionales ──────────────────────────────────────────
        if (!config.subtitleUri.isNullOrBlank()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse(config.subtitleUri)
            )
                .setMimeType(config.subtitleMimeType)
                .setLanguage("es")          // Español por defecto
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            Log.d(TAG, "Subtitle track attached: ${config.subtitleUri}")
        }

        player.apply {
            setMediaItem(mediaItemBuilder.build())
            seekTo(viewModel.restorePosition())
            playWhenReady = true
            prepare()
        }

        viewModel.setStreamUrl(config.url)
    }

    /** Pauses playback and persists the current position. */
    fun saveAndPause() {
        viewModel.saveCurrentPosition(player.currentPosition)
        player.pause()
    }

    /** Resumes playback from the current position. */
    fun resume() {
        player.play()
    }

    /** Releases ExoPlayer resources. Call from [PlayerActivity.onDestroy]. */
    fun release() {
        Log.d(TAG, "release")
        viewModel.saveCurrentPosition(player.currentPosition)
        player.release()
    }

    // ── Player.Listener ───────────────────────────────────────────────────────

    private fun playerListener() = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            Log.d(TAG, "playbackState=$state")
            when (state) {
                Player.STATE_BUFFERING -> {
                    viewModel.setStreamUrl(player.currentMediaItem?.localConfiguration?.uri?.toString() ?: "")
                    callback.onBuffering()
                }
                Player.STATE_READY     -> {
                    viewModel.onPlaybackReady()
                    callback.onReady()
                }
                Player.STATE_ENDED     -> callback.onEnded()
                Player.STATE_IDLE      -> { /* handled via onPlayerError if needed */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = error.message ?: "Unknown playback error"
            Log.e(TAG, "onPlayerError: $msg", error)
            viewModel.onPlaybackError(msg)
            callback.onError(msg)
        }
    }
}
