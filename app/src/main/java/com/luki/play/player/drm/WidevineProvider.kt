// player/drm/WidevineProvider.kt
package com.luki.play.player.drm

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import com.luki.play.player.DrmScheme
import com.luki.play.player.StreamConfig
import timber.log.Timber

/**
 * Construye [DrmSessionManager] para contenido protegido con Widevine.
 *
 * Diseño:
 *  - Recibe el [StreamConfig] del item a reproducir y produce un manager listo
 *    para inyectarse en `MediaSource.Factory.setDrmSessionManagerProvider`.
 *  - Para FTA / canales abiertos devuelve [DrmSessionManager.DRM_UNSUPPORTED]
 *    (no inicializa nada), de modo que coexisten clear y DRM en el mismo player.
 *  - L1 vs L3: ExoPlayer negocia automáticamente con `FrameworkMediaDrm`; el
 *    nivel real lo decide el dispositivo. La política "1080p L1 / 720p L3" del
 *    roadmap se aplicará vía selección de tracks cuando ese componente exista
 *    (hoy no hay TrackSelector propio en el proyecto), no aquí.
 *
 * NOTA: la API DRM de Media3 está marcada como `@UnstableApi`. Anotar a nivel de
 * clase para acotar el opt-in.
 */
@UnstableApi
class WidevineProvider(
    private val httpDataSourceFactory: DataSource.Factory,
) {

    /**
     * Devuelve un [DrmSessionManagerProvider] que entrega managers configurados
     * para [streamConfig]. Compatible con `MediaSource.Factory`.
     */
    fun provider(streamConfig: StreamConfig): DrmSessionManagerProvider =
        DrmSessionManagerProvider { _: MediaItem -> build(streamConfig) }

    /**
     * Construcción directa, útil para tests sin pasar por `MediaItem`.
     */
    fun build(streamConfig: StreamConfig): DrmSessionManager {
        if (streamConfig.drmScheme != DrmScheme.WIDEVINE || streamConfig.licenseUrl.isNullOrBlank()) {
            return DrmSessionManager.DRM_UNSUPPORTED
        }
        Timber.tag(TAG).d("Building Widevine session for %s", streamConfig.licenseUrl)

        val callback = HttpMediaDrmCallback(streamConfig.licenseUrl, httpDataSourceFactory).apply {
            streamConfig.licenseHeaders.forEach { (k, v) -> setKeyRequestProperty(k, v) }
        }

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(streamConfig.drmMultiSession)
            .build(callback)
    }

    companion object {
        private const val TAG = "WidevineProvider"
    }
}
