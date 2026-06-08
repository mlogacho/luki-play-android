// data/downloads/LukiDownloadService.kt
package com.luki.play.data.downloads

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import com.luki.play.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio en foreground que ejecuta descargas en segundo plano.
 *
 * Registrar en el manifest con `android:foregroundServiceType="dataSync"` y
 * `android:exported="false"`.
 *
 * Notification channel se crea en [com.luki.play.LukiApplication] al iniciar.
 */
@UnstableApi
@AndroidEntryPoint
class LukiDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    NOTIFICATION_CHANNEL_ID,
    R.string.app_name,
    0,
) {
    @Inject lateinit var injectedDownloadManager: DownloadManager

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): PlatformScheduler? =
        PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        // Notificación genérica — en sub-fase de UX se sustituye por una con
        // progreso por descarga.
        return androidx.core.app.NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Descargando contenido…")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "luki_downloads"
        const val FOREGROUND_NOTIFICATION_ID = 4_201
        private const val JOB_ID = 4_202
    }
}
