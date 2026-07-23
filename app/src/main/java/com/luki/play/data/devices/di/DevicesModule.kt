// data/devices/di/DevicesModule.kt
package com.luki.play.data.devices.di

import com.luki.play.data.devices.api.DevicesApi
import com.luki.play.data.network.di.AuthedRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DevicesModule {

    @Provides
    @Singleton
    fun provideDevicesApi(@AuthedRetrofit retrofit: Retrofit): DevicesApi =
        retrofit.create(DevicesApi::class.java)
}
