// data/network/AuthInterceptor.kt
package com.luki.play.data.network

import com.luki.play.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Añade `Authorization: Bearer <token>` a toda petición saliente cuando
 * existe un token y la request no marca explícitamente `No-Auth: true`.
 *
 * Para excluir un endpoint (por ejemplo login o refresh) basta con añadir
 * el header `No-Auth: true` a la definición Retrofit: este interceptor lo
 * detecta y lo elimina antes de continuar.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header(HEADER_NO_AUTH) != null) {
            val cleaned = original.newBuilder().removeHeader(HEADER_NO_AUTH).build()
            return chain.proceed(cleaned)
        }
        val token = tokenStore.accessToken() ?: return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }

    companion object {
        const val HEADER_NO_AUTH = "No-Auth"
    }
}
