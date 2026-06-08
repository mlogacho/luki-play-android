// tv/recommendations/RecommendationsWorker.kt
package com.luki.play.tv.recommendations

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.luki.play.data.catalog.ChannelsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker periódico que publica el catálogo destacado en el carril
 * "Luki Recomendados" del launcher Android TV.
 *
 * Se programa en [LukiApplication] / RouterActivity vía [enqueue].
 */
@HiltWorker
class RecommendationsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ChannelsRepository,
    private val publisher: RecommendationsPublisher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            repository.refresh()
            val channels = repository.observeChannels().first()
            publisher.publish(applicationContext, channels)
            Result.success()
        }.getOrElse {
            Timber.tag(TAG).w(it, "RecommendationsWorker failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RecommendationsWorker"
        private const val UNIQUE_NAME = "luki_recommendations_periodic"
        private const val INTERVAL_HOURS = 6L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RecommendationsWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
