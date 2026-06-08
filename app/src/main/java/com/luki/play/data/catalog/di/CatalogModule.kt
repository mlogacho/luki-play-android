// data/catalog/di/CatalogModule.kt
package com.luki.play.data.catalog.di

import android.content.Context
import androidx.room.Room
import com.luki.play.data.catalog.api.CatalogApi
import com.luki.play.data.catalog.db.CatalogDao
import com.luki.play.data.catalog.db.LukiDatabase
import com.luki.play.data.network.di.AuthedRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {

    @Provides
    @Singleton
    fun provideCatalogApi(@AuthedRetrofit retrofit: Retrofit): CatalogApi =
        retrofit.create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun provideLukiDatabase(@ApplicationContext context: Context): LukiDatabase =
        Room.databaseBuilder(context, LukiDatabase::class.java, "luki.db")
            .fallbackToDestructiveMigration()  // dev — en prod añadir migraciones reales
            .build()

    @Provides
    @Singleton
    fun provideCatalogDao(db: LukiDatabase): CatalogDao = db.catalogDao()
}
