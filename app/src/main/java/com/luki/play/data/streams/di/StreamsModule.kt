// data/streams/di/StreamsModule.kt
package com.luki.play.data.streams.di

import com.luki.play.data.network.di.AuthedRetrofit
import com.luki.play.data.streams.api.StreamSessionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamsModule {

    @Provides
    @Singleton
    fun provideStreamSessionApi(@AuthedRetrofit retrofit: Retrofit): StreamSessionApi =
        retrofit.create(StreamSessionApi::class.java)
}
