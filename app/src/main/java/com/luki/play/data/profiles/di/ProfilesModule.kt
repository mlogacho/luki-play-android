// data/profiles/di/ProfilesModule.kt
package com.luki.play.data.profiles.di

import com.luki.play.data.network.di.AuthedRetrofit
import com.luki.play.data.profiles.api.ProfilesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfilesModule {

    @Provides
    @Singleton
    fun provideProfilesApi(@AuthedRetrofit retrofit: Retrofit): ProfilesApi =
        retrofit.create(ProfilesApi::class.java)
}
