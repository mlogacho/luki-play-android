// data/downloads/DownloadsRepository.kt
package com.luki.play.data.downloads

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.player.StreamConfig
import com.luki.play.player.drm.OfflineLicenseAcquirer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fachada de descargas. Encapsula [DownloadManager] de Media3 detrás de una
 * API más amigable para el UI.
 *
 * NOTA importante (DRM): si el contenido es Widevine se debe primero solicitar
 * **license offline** (`OFFLINE_KEY_TYPE_OFFLINE`) y guardarla en el `DownloadRequest`
 * vía `setKeySetId(...)`. Eso requiere coordinación con el servidor de licencias
 * que aún no está en producción — queda como TODO marcado para sub-fase 5.x.
 */
@UnstableApi
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val offlineLicenseAcquirer: OfflineLicenseAcquirer,
) {

    /**
     * Encola una descarga.
     *
     * Si [config] indica Widevine + licenseUrl, primero adquiere la licencia
     * offline (keySetId) y la persiste en el `DownloadRequest`. Si la
     * adquisición falla, encolar igualmente la descarga para que el caller
     * pueda decidir si reintentar la licencia más tarde o eliminar el item.
     */
    suspend fun enqueue(channel: Channel, config: StreamConfig) {
        val keySetId: ByteArray? = if (config.hasDrm()) {
            offlineLicenseAcquirer.downloadLicense(config)
        } else null

        val builder = DownloadRequest.Builder(channel.id, android.net.Uri.parse(config.url))
            .setData(channel.name.toByteArray(Charsets.UTF_8))
        keySetId?.let { builder.setKeySetId(it) }

        DownloadService.sendAddDownload(
            context,
            LukiDownloadService::class.java,
            builder.build(),
            /* foreground = */ false,
        )
    }

    fun cancel(channelId: String) {
        DownloadService.sendRemoveDownload(
            context,
            LukiDownloadService::class.java,
            channelId,
            false,
        )
    }

    /** Observa el estado de todas las descargas. */
    fun observe(): Flow<List<Download>> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                trySend(downloadManager.currentDownloads)
                finalException?.let { Timber.tag(TAG).w(it, "download ${download.request.id} failed") }
            }
            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) {
                trySend(downloadManager.currentDownloads)
            }
        }
        downloadManager.addListener(listener)
        trySend(downloadManager.currentDownloads)
        awaitClose { downloadManager.removeListener(listener) }
    }

    companion object { private const val TAG = "DownloadsRepo" }
}
