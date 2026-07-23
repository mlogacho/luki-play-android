// data/network/di/NetworkModule.kt
package com.luki.play.data.network.di

import com.luki.play.BuildConfig
import com.luki.play.data.auth.SecureTokenStore
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.auth.api.AccountApi
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.network.AuthInterceptor
import com.luki.play.data.network.TokenAuthenticator
import com.luki.play.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module que ensambla el stack de red de Luki Play.
 *
 * Dos clientes Retrofit conviven:
 *  - [AuthedRetrofit]: con [AuthInterceptor] y [TokenAuthenticator].
 *    Es el que se usa para todas las llamadas autenticadas.
 *  - [PlainRetrofit]: sin authenticator. Lo usa [TokenAuthenticator] para
 *    invocar `/auth/refresh` sin caer en recursión.
 *
 * Ambos comparten OkHttp/Moshi salvo por los interceptors específicos.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    @PlainClient
    fun providePlainOkHttp(
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = baseClientBuilder(logging).build()

    @Provides
    @Singleton
    @AuthedClient
    fun provideAuthedOkHttp(
        logging: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = baseClientBuilder(logging)
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    @PlainRetrofit
    fun providePlainRetrofit(
        @PlainClient client: OkHttpClient,
        moshi: Moshi,
    ): Retrofit = baseRetrofit(client, moshi)

    @Provides
    @Singleton
    @AuthedRetrofit
    fun provideAuthedRetrofit(
        @AuthedClient client: OkHttpClient,
        moshi: Moshi,
    ): Retrofit = baseRetrofit(client, moshi)

    /**
     * AuthApi se inyecta a través del cliente "plain" porque la usa el
     * [TokenAuthenticator] para refrescar tokens. Las llamadas de login
     * no requieren Bearer, así que tampoco necesitan el cliente autenticado.
     */
    @Provides
    @Singleton
    fun provideAuthApi(@PlainRetrofit retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    /**
     * AccountApi (`/auth/me`, `/auth/change-password`) SÍ requiere Bearer, así
     * que se crea sobre el cliente autenticado —a diferencia de [AuthApi], que
     * es "plain" para login/refresh—.
     */
    @Provides
    @Singleton
    fun provideAccountApi(@AuthedRetrofit retrofit: Retrofit): AccountApi =
        retrofit.create(AccountApi::class.java)

    private fun baseClientBuilder(logging: HttpLoggingInterceptor): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)

    private fun baseRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(Constants.API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}

/**
 * Bindings de interfaces a implementaciones. Separados en su propio módulo
 * porque [Binds] requiere clases abstractas y mezclarlo con [Provides] obliga
 * a convertir todo el módulo.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: SecureTokenStore): TokenStore
}

// ── Qualifiers ────────────────────────────────────────────────────────────────

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthedClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthedRetrofit
