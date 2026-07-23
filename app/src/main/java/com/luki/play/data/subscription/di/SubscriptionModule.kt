// data/subscription/di/SubscriptionModule.kt
package com.luki.play.data.subscription.di

import com.luki.play.data.network.di.AuthedRetrofit
import com.luki.play.data.subscription.api.SubscriptionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionModule {

    @Provides
    @Singleton
    fun provideSubscriptionApi(@AuthedRetrofit retrofit: Retrofit): SubscriptionApi =
        retrofit.create(SubscriptionApi::class.java)
}
