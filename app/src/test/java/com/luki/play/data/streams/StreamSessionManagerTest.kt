// test/data/streams/StreamSessionManagerTest.kt
package com.luki.play.data.streams

import com.luki.play.data.auth.FakeTokenStore
import com.luki.play.data.streams.api.StartStreamRequest
import com.luki.play.data.streams.api.StartStreamResponse
import com.luki.play.data.streams.api.StreamSessionApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Comprueba que el ciclo de sesión replica al portal, que es lo que aplica el
 * tope de streams simultáneos del plan.
 */
class StreamSessionManagerTest {

    private class FakeStreamApi(
        var startCode: Int = 200,
        var stopCode: Int = 204,
    ) : StreamSessionApi {

        var startCalls = mutableListOf<StartStreamRequest>()
        var stopCalls = mutableListOf<String>()
        var nextStreamId = "sesion-1"

        override suspend fun start(body: StartStreamRequest): StartStreamResponse {
            startCalls += body
            if (startCode !in 200..299) throw httpException(startCode)
            return StartStreamResponse(nextStreamId)
        }

        override suspend fun heartbeat(streamId: String) = Response.success(Unit)

        override suspend fun stop(streamId: String): Response<Unit> {
            stopCalls += streamId
            return if (stopCode in 200..299) Response.success(Unit)
            else Response.error(stopCode, EMPTY_BODY)
        }

        private fun httpException(code: Int) =
            HttpException(Response.error<Unit>(code, EMPTY_BODY))
    }

    private fun TestScope.manager(api: StreamSessionApi) = StreamSessionManager(
        api = api,
        tokenStore = FakeTokenStore(),
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `abrir sesion manda channelId y deviceId y guarda el streamId`() = runTest {
        val api = FakeStreamApi()
        val manager = manager(api)

        val result = manager.open("canal-1")

        assertEquals(StreamSessionManager.OpenResult.Started, result)
        assertEquals("canal-1", api.startCalls.single().channelId)
        assertEquals("device-test", api.startCalls.single().deviceId)
        assertEquals("sesion-1", manager.currentStreamId)
    }

    @Test
    fun `un 429 se distingue como tope alcanzado`() = runTest {
        val api = FakeStreamApi(startCode = 429)
        val manager = manager(api)

        val result = manager.open("canal-1")

        assertEquals(
            "el tope del plan necesita UI propia, no puede confundirse con un error generico",
            StreamSessionManager.OpenResult.LimitReached,
            result,
        )
        assertNull(manager.currentStreamId)
    }

    @Test
    fun `otro error HTTP no se confunde con el tope`() = runTest {
        val api = FakeStreamApi(startCode = 500)
        val manager = manager(api)

        val result = manager.open("canal-1")

        assertTrue(result is StreamSessionManager.OpenResult.Failed)
    }

    @Test
    fun `cerrar libera el cupo y olvida la sesion`() = runTest {
        val api = FakeStreamApi()
        val manager = manager(api)
        manager.open("canal-1")

        manager.close()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("sesion-1"), api.stopCalls)
        assertNull(manager.currentStreamId)
    }

    @Test
    fun `cambiar de canal reutiliza el lease en vez de acumular sesiones`() = runTest {
        // El backend hace upsert por (cliente, deviceId): el zapping NO debe
        // consumir cupos extra, solo actualizar el canal del lease.
        val api = FakeStreamApi()
        val manager = manager(api)

        manager.open("canal-1")
        manager.open("canal-2")

        assertEquals(2, api.startCalls.size)
        assertEquals(listOf("canal-1", "canal-2"), api.startCalls.map { it.channelId })
        assertEquals(
            "el deviceId debe ser el mismo para que el backend reutilice el lease",
            1,
            api.startCalls.map { it.deviceId }.distinct().size,
        )
    }

    private companion object {
        val EMPTY_BODY = "".toResponseBody("text/plain".toMediaType())
    }
}
