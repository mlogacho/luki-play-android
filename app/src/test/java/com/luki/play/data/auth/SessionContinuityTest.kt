// test/data/auth/SessionContinuityTest.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.RefreshRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Continuidad de sesión en el rollout web → nativo.
 *
 * Hoy la sesión nace en el portal, y un subscriber del authStore la replica
 * al almacén nativo vía `LukiBridge.onLoginSuccess` (accessToken +
 * refreshToken + userId + displayName). Cuando ese mismo usuario reciba un
 * build con `NATIVE_HOME_ENABLED=true`, el NavGraph elige startDestination
 * según [AuthRepository.current]: si no reconociera la sesión replicada,
 * el rollout forzaría a re-loguear a TODA la base instalada.
 *
 * Estos tests fijan esa garantía.
 */
class SessionContinuityTest {

    private fun repo(store: TokenStore) =
        AuthRepository(NoopAuthApi(), FakeAccountApi(), store, Dispatchers.Unconfined)

    @Test
    fun `una sesion replicada por el portal se reconoce como autenticada`() {
        // Estado tal como lo deja onLoginSuccess del bridge.
        val store = FakeTokenStore(
            initialAccess      = "jwt-de-la-web",
            initialRefresh     = "refresh-de-la-web",
            initialUserId      = "cus_123",
            initialDisplayName = "Carlos Paz",
        )

        val state = repo(store).current()

        assertTrue("el build nativo debe entrar sin re-login", state is SessionState.Authenticated)
        state as SessionState.Authenticated
        assertEquals("cus_123", state.userId)
        assertEquals("Carlos Paz", state.displayName)
    }

    @Test
    fun `sin tokens el estado es anonimo y el grafo debe mandar a LOGIN`() {
        assertTrue(repo(FakeTokenStore()).current() is SessionState.Anonymous)
    }

    @Test
    fun `un accessToken en blanco no cuenta como sesion`() {
        val store = FakeTokenStore(initialAccess = "   ", initialRefresh = "r")
        assertTrue(repo(store).current() is SessionState.Anonymous)
    }

    @Test
    fun `una sesion replicada sin displayName sigue siendo valida`() {
        // El portal puede replicar userId/displayName vacíos (user?.name ?? '').
        val store = FakeTokenStore(initialAccess = "jwt", initialRefresh = "r")

        val state = repo(store).current()

        assertTrue(state is SessionState.Authenticated)
        assertEquals("", (state as SessionState.Authenticated).displayName)
    }
}

/** [AuthApi] que falla si se la llama: estos tests no deben tocar la red. */
private class NoopAuthApi : AuthApi {
    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto = error("no debe llamarse")
    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto = error("no debe llamarse")
    override suspend fun refresh(body: RefreshRequest): AuthResponseDto = error("no debe llamarse")
    override suspend fun logout() = error("no debe llamarse")
    override suspend fun requestPasswordOtp(body: RequestPasswordOtpRequest): MessageResponseDto = error("no debe llamarse")
    override suspend fun resetPasswordWithOtp(body: ResetPasswordOtpRequest): MessageResponseDto = error("no debe llamarse")
    override suspend fun firstAccess(body: com.luki.play.data.auth.api.FirstAccessRequest) = error("no debe llamarse")
    override suspend fun requestActivationCode(body: com.luki.play.data.auth.api.RequestActivationCodeRequest) = error("no debe llamarse")
    override suspend fun verifyActivationCode(body: com.luki.play.data.auth.api.VerifyActivationCodeRequest) = error("no debe llamarse")
    override suspend fun activate(body: com.luki.play.data.auth.api.ActivateRequest) = error("no debe llamarse")
    override suspend fun submitRegistrationRequest(body: com.luki.play.data.auth.api.RegistrationRequestBody) = error("no debe llamarse")
}
