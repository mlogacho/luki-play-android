// data/favorites/di/FavoritesModule.kt
package com.luki.play.data.favorites.di

import com.luki.play.data.favorites.api.FavoritesApi
import com.luki.play.data.network.di.AuthedRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FavoritesModule {

    @Provides
    @Singleton
    fun provideFavoritesApi(@AuthedRetrofit retrofit: Retrofit): FavoritesApi =
        retrofit.create(FavoritesApi::class.java)
}
