// test/data/network/AuthInterceptorTest.kt
package com.luki.play.data.network

import com.luki.play.data.auth.FakeTokenStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds bearer header when token present`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val store = FakeTokenStore(initialAccess = "tok-123")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()

        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer tok-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `omits bearer when no token`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val store = FakeTokenStore(initialAccess = null)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()

        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `No-Auth header skips bearer and is stripped`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val store = FakeTokenStore(initialAccess = "tok-xyz")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()

        val req = Request.Builder()
            .url(server.url("/login"))
            .header(AuthInterceptor.HEADER_NO_AUTH, "true")
            .build()
        client.newCall(req).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader(AuthInterceptor.HEADER_NO_AUTH))
    }
}
