// cast/CastController.kt
package com.luki.play.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.luki.play.player.StreamConfig
import timber.log.Timber

/**
 * Gestiona el ciclo de vida de Cast en una Activity de reproducción.
 *
 * Uso desde [com.luki.play.player.PlayerActivity]:
 * ```
 * private val castController = CastController(this)
 * override fun onResume() { super.onResume(); castController.attach() }
 * override fun onPause()  { castController.detach(); super.onPause() }
 * ```
 *
 * Diseño:
 *  - `lazy` para no forzar Google Play Services en dispositivos sin Cast
 *    (TV boxes baratos que no la traen) — `CastContext.getSharedInstance`
 *    lanza `IllegalStateException` allí y se gestiona.
 *  - [castPlayer] expone un [androidx.media3.cast.CastPlayer] cuando hay
 *    sesión Cast activa; el caller decide cuándo intercambiarlo con su
 *    `ExoPlayer` local para una transición fluida (patrón estándar de
 *    `PlayerView.setPlayer(castPlayer ?: localPlayer)`).
 */
@UnstableApi
class CastController(private val context: Context) {

    private var castContext: CastContext? = null
    private var stateListener: CastStateListener? = null

    /** Disponible si Google Play Services + Cast Framework están presentes. */
    val isAvailable: Boolean get() = castContext != null

    /** Player a usar cuando hay sesión Cast — null si no. */
    val castPlayer: CastPlayer? by lazy {
        castContext?.let { CastPlayer(it) }
    }

    fun attach() {
        if (castContext == null) {
            castContext = runCatching { CastContext.getSharedInstance(context) }
                .onFailure { Timber.tag(TAG).w(it, "CastContext no disponible — Cast deshabilitado") }
                .getOrNull()
        }
        stateListener = CastStateListener { state ->
            Timber.tag(TAG).d("Cast state changed: %s", castStateName(state))
        }.also { castContext?.addCastStateListener(it) }
    }

    fun detach() {
        stateListener?.let { castContext?.removeCastStateListener(it) }
        stateListener = null
    }

    /** Encola la fuente actual en el [CastPlayer] activo, si lo hay. */
    fun loadOnCast(config: StreamConfig): Boolean {
        val player = castPlayer ?: return false
        val mediaItem = MediaItem.Builder()
            .setUri(config.url)
            .setMimeType(mimeTypeFor(config))
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(config.title.ifBlank { "Luki Play" }).build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.playWhenReady = true
        player.prepare()
        return true
    }

    private fun mimeTypeFor(config: StreamConfig): String = when (config.effectiveManifestType()) {
        com.luki.play.player.ManifestType.HLS  -> MimeTypes.APPLICATION_M3U8
        com.luki.play.player.ManifestType.DASH -> MimeTypes.APPLICATION_MPD
        else                                    -> MimeTypes.APPLICATION_MP4
    }

    private fun castStateName(state: Int): String = when (state) {
        CastState.NO_DEVICES_AVAILABLE -> "NO_DEVICES"
        CastState.NOT_CONNECTED        -> "NOT_CONNECTED"
        CastState.CONNECTING           -> "CONNECTING"
        CastState.CONNECTED            -> "CONNECTED"
        else                            -> "UNKNOWN($state)"
    }

    companion object { private const val TAG = "CastController" }
}
