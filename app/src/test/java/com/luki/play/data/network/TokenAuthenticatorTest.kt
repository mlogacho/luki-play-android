// test/data/network/TokenAuthenticatorTest.kt
package com.luki.play.data.network

import com.luki.play.data.auth.FakeTokenStore
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.RefreshRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Provider

class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `on 401 refreshes token and retries request`() {
        server.enqueue(MockResponse().setResponseCode(401))   // primer intento
        server.enqueue(MockResponse().setResponseCode(200))   // retry exitoso

        val store = FakeTokenStore(initialAccess = "old", initialRefresh = "ref-1")
        val api = RefreshingAuthApi(
            refreshResponse = AuthResponseDto(
                accessToken  = "new-token",
                refreshToken = "ref-2",
                userId       = null,
                idAlt        = null,
                nombre       = null,
                nameAlt      = null,
            )
        )
        val authenticator = TokenAuthenticator(store, Provider { api })

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()

        val response = client.newCall(
            Request.Builder().url(server.url("/secret")).build()
        ).execute()
        response.close()

        // El primer request fue con el token viejo
        val first = server.takeRequest()
        assertEquals("Bearer old", first.getHeader("Authorization"))

        // El segundo (retry) lleva el token nuevo
        val second = server.takeRequest()
        assertEquals("Bearer new-token", second.getHeader("Authorization"))

        // El store fue actualizado
        assertEquals("new-token", store.accessToken())
        assertEquals("ref-2", store.refreshToken())
        assertEquals(1, api.refreshCalls)
        assertEquals(1, store.updateCount)
    }

    @Test
    fun `no refresh token clears nothing and returns null`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = FakeTokenStore(initialAccess = "tok", initialRefresh = null)
        val api = RefreshingAuthApi()
        val authenticator = TokenAuthenticator(store, Provider { api })

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()

        val response = client.newCall(
            Request.Builder().url(server.url("/secret")).build()
        ).execute()

        assertEquals(401, response.code)
        response.close()
        assertEquals(0, api.refreshCalls)
        assertEquals("tok", store.accessToken())   // no se limpió
    }

    @Test
    fun `refresh rejected by server clears session`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = FakeTokenStore(initialAccess = "tok", initialRefresh = "ref")
        val api = RefreshingAuthApi(refreshException = httpException(401))
        val authenticator = TokenAuthenticator(store, Provider { api })

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()

        val response = client.newCall(
            Request.Builder().url(server.url("/secret")).build()
        ).execute()
        response.close()

        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertEquals(1, store.clearCount)
    }

    @Test
    fun `refresh network failure keeps session`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = FakeTokenStore(initialAccess = "tok", initialRefresh = "ref")
        val api = RefreshingAuthApi(refreshException = IOException("sin conexión"))
        val authenticator = TokenAuthenticator(store, Provider { api })

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()

        val response = client.newCall(
            Request.Builder().url(server.url("/secret")).build()
        ).execute()

        // El 401 pasa al caller, pero la sesión sigue intacta para reintentar
        assertEquals(401, response.code)
        response.close()
        assertEquals("tok", store.accessToken())
        assertEquals("ref", store.refreshToken())
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `refresh server error keeps session`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = FakeTokenStore(initialAccess = "tok", initialRefresh = "ref")
        val api = RefreshingAuthApi(refreshException = httpException(503))
        val authenticator = TokenAuthenticator(store, Provider { api })

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()

        val response = client.newCall(
            Request.Builder().url(server.url("/secret")).build()
        ).execute()
        response.close()

        assertEquals("tok", store.accessToken())
        assertEquals("ref", store.refreshToken())
        assertEquals(0, store.clearCount)
    }

    private fun httpException(code: Int): HttpException =
        HttpException(retrofit2.Response.error<Any>(code, "".toResponseBody()))
}

private class RefreshingAuthApi(
    private val refreshResponse: AuthResponseDto? = null,
    private val refreshException: Throwable? = null,
) : AuthApi {

    var refreshCalls = 0
        private set

    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto =
        error("not used")
    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto =
        error("not used")
    override suspend fun logout() { /* no-op */ }

    override suspend fun refresh(body: RefreshRequest): AuthResponseDto {
        refreshCalls++
        refreshException?.let { throw it }
        return refreshResponse ?: error("refreshResponse no configurado")
    }
}
