// data/downloads/DownloadModule.kt
package com.luki.play.data.downloads

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Hilt module que ensambla el stack de descargas offline (Media3).
 *
 * Componentes:
 *  - [SimpleCache] sobre `filesDir/downloads` con [NoOpCacheEvictor] (no se
 *    desalojan descargas a propósito; el desalojo lo gestionará el usuario).
 *  - [DefaultDownloadIndex] sobre [StandaloneDatabaseProvider] (SQLite).
 *  - [DownloadManager] con factory por defecto y requisito de **red no medida**
 *    (i.e. solo Wi-Fi) — ajustable luego con una pantalla de ajustes.
 */
@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): StandaloneDatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        dbProvider: StandaloneDatabaseProvider,
    ): Cache {
        val cacheDir = File(context.filesDir, "downloads")
        return SimpleCache(cacheDir, NoOpCacheEvictor(), dbProvider)
    }

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory().setUserAgent("LukiPlay-Android")

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: Cache,
        httpFactory: DataSource.Factory,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        dbProvider: StandaloneDatabaseProvider,
        cache: Cache,
        httpFactory: DataSource.Factory,
    ): DownloadManager {
        val executor = Executors.newFixedThreadPool(2)
        val mgr = DownloadManager(
            context,
            DefaultDownloadIndex(dbProvider),
            DefaultDownloaderFactory(
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpFactory),
                executor,
            ),
        )
        // Solo descargar en Wi-Fi por defecto
        mgr.requirements = Requirements(Requirements.NETWORK_UNMETERED)
        return mgr
    }
}
