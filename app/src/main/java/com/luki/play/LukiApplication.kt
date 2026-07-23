// LukiApplication.kt
package com.luki.play

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.luki.play.data.config.FeatureFlags
import com.luki.play.data.downloads.LukiDownloadService
import com.luki.play.tv.recommendations.RecommendationsWorker
import com.luki.play.util.CrashlyticsTree
import com.luki.play.util.DeviceUtils
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LukiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var featureFlags: FeatureFlags

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initTimber()
        initWebView()
        initDownloadNotificationChannel()
        scheduleTvRecommendations()
        // Actualiza los flags en segundo plano; el valor nuevo aplica en el
        // PRÓXIMO arranque. Este arranque usa el último valor cacheado.
        featureFlags.refresh()
    }

    private fun scheduleTvRecommendations() {
        // Solo en dispositivos TV: la API tvprovider sólo aplica allí.
        if (DeviceUtils.isTv(this)) {
            RecommendationsWorker.enqueue(this)
        }
    }

    private fun initDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            LukiDownloadService.NOTIFICATION_CHANNEL_ID,
            "Descargas",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Progreso de descargas offline" }
        nm.createNotificationChannel(channel)
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            return
        }
        // Release: WARN/ERROR van a Crashlytics como breadcrumbs/non-fatals.
        // Firebase solo se inicializa si el build incluyó google-services.json;
        // si no, no plantamos nada y los Timber.* siguen siendo no-ops.
        runCatching {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                Timber.plant(CrashlyticsTree())
            }
        }
    }

    private fun initWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            Timber.d("WebView debugging enabled (debug build)")
        }
    }
}
