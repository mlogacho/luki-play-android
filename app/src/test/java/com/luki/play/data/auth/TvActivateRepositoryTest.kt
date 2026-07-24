// test/data/auth/TvActivateRepositoryTest.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.TvActivateRequest
import com.luki.play.data.auth.api.TvActivateResultDto
import com.luki.play.data.auth.api.TvAuthApi
import com.luki.play.data.auth.api.TvPollDto
import com.luki.play.data.auth.api.TvSessionDto
import com.luki.play.data.auth.api.TvSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Lado teléfono del emparejamiento ("Activar TV"). Ancla que el código se manda
 * normalizado (trim + mayúsculas, como `tvCode.trim().toUpperCase()` del portal)
 * y que `requiresActivation` se traduce a [TvActivateOutcome.NeedsAccountActivation]
 * en vez de darse por conectado.
 */
class TvActivateRepositoryTest {

    private fun repo(api: FakeTvApi) = TvAuthRepository(api, FakeTokenStore(), Dispatchers.Unconfined)

    @Test
    fun `normaliza el codigo a mayusculas y sin espacios`() = runTest {
        val api = FakeTvApi()

        repo(api).activateTv("  ab3k7p  ", "  1720345678 ", "clave").getOrThrow()

        val req = api.lastActivate!!
        assertEquals("AB3K7P", req.code)
        assertEquals("1720345678", req.idNumber)
        assertEquals("clave", req.password)
    }

    @Test
    fun `ok conecta el TV`() = runTest {
        val outcome = repo(FakeTvApi(result = TvActivateResultDto(ok = true))).
            activateTv("AB3K7P", "1720345678", "clave").getOrThrow()

        assertEquals(TvActivateOutcome.Connected, outcome)
    }

    @Test
    fun `requiresActivation NO conecta y pide activar la cuenta`() = runTest {
        val api = FakeTvApi(result = TvActivateResultDto(ok = false, requiresActivation = true))

        val outcome = repo(api).activateTv("AB3K7P", "1720345678", "temporal").getOrThrow()

        assertEquals(TvActivateOutcome.NeedsAccountActivation, outcome)
    }

    @Test
    fun `fallo de red se propaga como Result_failure`() = runTest {
        val api = FakeTvApi(failWith = IOException("timeout"))

        assertTrue(repo(api).activateTv("AB3K7P", "1720345678", "clave").isFailure)
    }
}

private class FakeTvApi(
    private val result: TvActivateResultDto = TvActivateResultDto(ok = true),
    private val failWith: Throwable? = null,
) : TvAuthApi {
    var lastActivate: TvActivateRequest? = null; private set

    override suspend fun activateTv(body: TvActivateRequest): TvActivateResultDto {
        lastActivate = body
        failWith?.let { throw it }
        return result
    }

    override suspend fun createSession(body: TvSessionRequest): TvSessionDto = error("no usado")
    override suspend fun poll(sessionId: String): TvPollDto = error("no usado")
}
