// data/config/di/ConfigModule.kt
package com.luki.play.data.config.di

import com.luki.play.data.config.FeatureFlags
import com.luki.play.data.config.RemoteFeatureFlags
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigModule {

    @Binds
    @Singleton
    abstract fun bindFeatureFlags(impl: RemoteFeatureFlags): FeatureFlags
}
