// test/data/auth/AuthRepositoryTest.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.RefreshRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests del [AuthRepository] usando un [FakeAuthApi] y [FakeTokenStore].
 * No requieren Robolectric.
 */
class AuthRepositoryTest {

    @Test
    fun `loginWithId persiste sesion y emite Authenticated`() = runTest {
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto(
                accessToken  = "a1",
                refreshToken = "r1",
                userId       = "u1",
                idAlt        = null,
                nombre       = "Sofia",
                nameAlt      = null,
            )
        )
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, store)

        val result = repo.loginWithId("0102", "secret")

        assertTrue("login result should be success", result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("a1", session.accessToken)
        assertEquals("r1", session.refreshToken)
        assertEquals("u1", session.userId)
        assertEquals("Sofia", session.displayName)

        // Persistido
        assertEquals("a1", store.accessToken())
        assertEquals("r1", store.refreshToken())
        assertEquals("u1", store.userId())
        assertEquals("Sofia", store.displayName())

        // Estado autenticado
        val state = repo.current()
        assertTrue(state is SessionState.Authenticated)
        assertEquals("u1", (state as SessionState.Authenticated).userId)

        // Request body construido con deviceId del store
        val req = api.lastIdRequest!!
        assertEquals("0102", req.idNumber)
        assertEquals("secret", req.password)
        assertEquals(store.deviceId(), req.deviceId)
    }

    @Test
    fun `loginWithContract usa endpoint correcto`() = runTest {
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto("a", null, "u", null, null, "Nick")
        )
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, store)

        repo.loginWithContract("C-99", "pwd").getOrThrow()

        assertEquals(0, api.idLoginCalls)
        assertEquals(1, api.contractLoginCalls)
        assertEquals("C-99", api.lastContractRequest!!.contractNumber)
    }

    @Test
    fun `login fallido devuelve Result failure sin tocar store`() = runTest {
        val api = FakeAuthApi(loginException = IOException("boom"))
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, store)

        val result = repo.loginWithId("0102", "x")

        assertTrue(result.isFailure)
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `logout limpia store incluso si endpoint remoto falla`() = runTest {
        val api = FakeAuthApi(logoutException = IOException("offline"))
        val store = FakeTokenStore(
            initialAccess  = "a",
            initialRefresh = "r",
            initialUserId  = "u",
            initialDisplayName = "n",
        )
        val repo = AuthRepository(api, store)

        val result = repo.logout()

        assertTrue(result.isSuccess)
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertEquals(1, store.clearCount)
        assertTrue(repo.current() is SessionState.Anonymous)
    }
}

/**
 * Implementación de [AuthApi] controlable desde tests.
 */
private class FakeAuthApi(
    private val loginResponse: AuthResponseDto? = null,
    private val loginException: Throwable? = null,
    private val refreshResponse: AuthResponseDto? = null,
    private val logoutException: Throwable? = null,
) : AuthApi {

    var idLoginCalls = 0
        private set
    var contractLoginCalls = 0
        private set
    var refreshCalls = 0
        private set
    var lastIdRequest: IdLoginRequest? = null
        private set
    var lastContractRequest: ContractLoginRequest? = null
        private set
    var lastRefreshRequest: RefreshRequest? = null
        private set

    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto {
        idLoginCalls++
        lastIdRequest = body
        loginException?.let { throw it }
        return loginResponse ?: error("FakeAuthApi: loginResponse no configurado")
    }

    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto {
        contractLoginCalls++
        lastContractRequest = body
        loginException?.let { throw it }
        return loginResponse ?: error("FakeAuthApi: loginResponse no configurado")
    }

    override suspend fun refresh(body: RefreshRequest): AuthResponseDto {
        refreshCalls++
        lastRefreshRequest = body
        return refreshResponse ?: error("FakeAuthApi: refreshResponse no configurado")
    }

    override suspend fun logout() {
        logoutException?.let { throw it }
    }
}
