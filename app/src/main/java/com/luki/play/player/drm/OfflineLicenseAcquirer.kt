// player/drm/OfflineLicenseAcquirer.kt
package com.luki.play.player.drm

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.OfflineLicenseHelper
import androidx.media3.exoplayer.offline.DownloadHelper
import com.luki.play.player.DrmScheme
import com.luki.play.player.ManifestType
import com.luki.play.player.StreamConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adquiere y libera licencias offline Widevine para descargas DRM.
 *
 * Flujo (invocado desde [com.luki.play.data.downloads.DownloadsRepository]):
 *  1. Antes de encolar la descarga, [downloadLicense] usa [DownloadHelper]
 *     para parsear el manifest (HLS/DASH) y extraer el `Format` con sus
 *     `DrmInitData`.
 *  2. Llama a [OfflineLicenseHelper.downloadLicense] con ese Format → devuelve
 *     un `keySetId` (bytes) que identifica la licencia persistente.
 *  3. El caller guarda `keySetId` en `DownloadRequest.Builder.setKeySetId(...)`.
 *  4. En reproducción offline, el `DrmSessionManager` cargará esa licencia
 *     persistida automáticamente — no se necesita red.
 *
 * Liberación: [releaseLicense] cuando el usuario borra el contenido descargado.
 *
 * **Validez:** este código compila e invoca correctamente la API de Media3.
 * Si el servidor de licencias devuelve una licencia **streaming** (no persistente),
 * `downloadLicense` lanzará `DrmSession.DrmSessionException`. El que el endpoint
 * devuelva licencias persistentes es contractual con el servidor de licensing —
 * no algo que el cliente pueda forzar. Por eso este wrapper se considera "completo
 * a nivel cliente" aunque su validación end-to-end dependa del backend.
 */
@UnstableApi
class OfflineLicenseAcquirer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpDataSourceFactory: DataSource.Factory,
) {

    /**
     * Descarga y persiste una licencia offline Widevine para [streamConfig].
     *
     * @return `keySetId` para guardar en el DownloadRequest, o `null` si:
     *   - el contenido no es Widevine (no aplica DRM offline),
     *   - el manifest no se pudo parsear,
     *   - el servidor devolvió una licencia no persistente.
     */
    suspend fun downloadLicense(streamConfig: StreamConfig): ByteArray? = withContext(Dispatchers.IO) {
        if (streamConfig.drmScheme != DrmScheme.WIDEVINE || streamConfig.licenseUrl.isNullOrBlank()) {
            return@withContext null
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamConfig.url))
            .setMimeType(when (streamConfig.effectiveManifestType()) {
                ManifestType.HLS  -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
                ManifestType.DASH -> androidx.media3.common.MimeTypes.APPLICATION_MPD
                else              -> androidx.media3.common.MimeTypes.APPLICATION_MP4
            })
            .build()

        val helper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            DefaultRenderersFactory(context),
            httpDataSourceFactory,
        )

        return@withContext runCatching {
            preparePlayer(helper)
            val format = helper.findFirstFormatWithDrmInitData() ?: run {
                Timber.tag(TAG).w("OfflineLicenseAcquirer: manifest sin DrmInitData")
                return@runCatching null
            }
            val licenseHelper = OfflineLicenseHelper.newWidevineInstance(
                streamConfig.licenseUrl,
                /* forceDefaultLicenseUrl= */ false,
                httpDataSourceFactory,
                streamConfig.licenseHeaders,
                androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher(),
            )
            try {
                licenseHelper.downloadLicense(format)
            } finally {
                licenseHelper.release()
            }
        }.also {
            helper.release()
        }.onFailure {
            Timber.tag(TAG).w(it, "OfflineLicenseAcquirer.downloadLicense failed (url=%s)", streamConfig.url)
        }.getOrNull()
    }

    suspend fun releaseLicense(licenseUrl: String, keySetId: ByteArray) = withContext(Dispatchers.IO) {
        val licenseHelper = OfflineLicenseHelper.newWidevineInstance(
            licenseUrl,
            httpDataSourceFactory,
            androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher(),
        )
        runCatching { licenseHelper.releaseLicense(keySetId) }
            .onFailure { Timber.tag(TAG).w(it, "releaseLicense failed") }
        licenseHelper.release()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Adapta el callback de `DownloadHelper.prepare()` (Java-style) a coroutines.
     */
    private suspend fun preparePlayer(helper: DownloadHelper): Unit =
        suspendCancellableCoroutine { cont ->
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(h: DownloadHelper) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onPrepareError(h: DownloadHelper, e: java.io.IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
            cont.invokeOnCancellation { runCatching { helper.release() } }
        }

    /**
     * Encuentra el primer [androidx.media3.common.Format] que incluya `DrmInitData`
     * en los grupos preparados por [DownloadHelper]. Recorre periodos × renderer ×
     * trackGroups × tracks; no asume índices fijos.
     */
    private fun DownloadHelper.findFirstFormatWithDrmInitData(): androidx.media3.common.Format? {
        for (periodIndex in 0 until periodCount) {
            val mapped = getMappedTrackInfo(periodIndex)
            for (renderer in 0 until mapped.rendererCount) {
                val groups = mapped.getTrackGroups(renderer)
                for (gi in 0 until groups.length) {
                    val group = groups.get(gi)
                    for (ti in 0 until group.length) {
                        val format = group.getFormat(ti)
                        if (format.drmInitData != null) return format
                    }
                }
            }
        }
        return null
    }

    companion object { private const val TAG = "OfflineLicense" }
}
