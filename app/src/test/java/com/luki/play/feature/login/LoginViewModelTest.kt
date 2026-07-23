// test/feature/login/LoginViewModelTest.kt
package com.luki.play.feature.login

import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.AuthUserDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.RefreshRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Verifica que el login nativo replica el comportamiento del front web
 * (login.tsx): validación mínima (trim + requerido, sin checksum de cédula
 * — conviven cédulas de 10 y RUC de 13), mismos mensajes de error y
 * request idéntico campo a campo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: FakeAuthApi): Pair<LoginViewModel, FakeAuthApi> =
        LoginViewModel(AuthRepository(api, FakeTokenStore(), dispatcher)) to api

    private fun okResponse() = AuthResponseDto(
        accessToken  = "a",
        refreshToken = "r",
        user         = AuthUserDto("u1", "Sofia", null, "lukiplay"),
    )

    @Test
    fun `cedula vacia muestra el mismo mensaje que el front sin llamar a la API`() = runTest(dispatcher) {
        val (vm, api) = vm(FakeAuthApi(loginResponse = okResponse()))

        vm.login("   ", "secret")

        assertEquals("La cédula es requerida", vm.uiState.value.errorMessage)
        assertEquals(0, api.idLoginCalls)
    }

    @Test
    fun `contrasena vacia muestra su mensaje propio`() = runTest(dispatcher) {
        val (vm, api) = vm(FakeAuthApi(loginResponse = okResponse()))

        vm.login("1720345678", "")

        assertEquals("La contraseña es requerida", vm.uiState.value.errorMessage)
        assertEquals(0, api.idLoginCalls)
    }

    @Test
    fun `login con cedula hace trim y termina en loggedIn`() = runTest(dispatcher) {
        val (vm, api) = vm(FakeAuthApi(loginResponse = okResponse()))

        vm.login("  1720345678  ", "secret")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.loggedIn)
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        // Fidelidad de request: mismo shape que el front.
        assertEquals("1720345678", api.lastIdRequest!!.idNumber)
        assertEquals("secret", api.lastIdRequest!!.password)
    }

    @Test
    fun `un RUC de 13 digitos no se bloquea localmente`() = runTest(dispatcher) {
        val (vm, api) = vm(FakeAuthApi(loginResponse = okResponse()))

        vm.login("1790012345001", "secret")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, api.idLoginCalls)
        assertTrue(vm.uiState.value.loggedIn)
    }

    @Test
    fun `la pantalla solo usa el endpoint de cedula, como el portal`() = runTest(dispatcher) {
        val (vm, api) = vm(FakeAuthApi(loginResponse = okResponse()))

        vm.login("1720345678", "pwd")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, api.idLoginCalls)
        assertEquals(0, api.contractLoginCalls)
    }

    @Test
    fun `fallo de red muestra el mensaje de sin conexion del front`() = runTest(dispatcher) {
        val (vm, _) = vm(FakeAuthApi(loginException = IOException("timeout")))

        vm.login("1720345678", "secret")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertFalse(s.loggedIn)
        assertFalse(s.isLoading)
        assertEquals("Sin conexión. Verifica tu internet e intenta de nuevo.", s.errorMessage)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

private class FakeAuthApi(
    private val loginResponse: AuthResponseDto? = null,
    private val loginException: Throwable? = null,
) : AuthApi {
    var idLoginCalls = 0; private set
    var contractLoginCalls = 0; private set
    var lastIdRequest: IdLoginRequest? = null; private set
    var lastContractRequest: ContractLoginRequest? = null; private set

    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto {
        idLoginCalls++; lastIdRequest = body
        loginException?.let { throw it }
        return loginResponse ?: error("loginResponse no configurado")
    }

    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto {
        contractLoginCalls++; lastContractRequest = body
        loginException?.let { throw it }
        return loginResponse ?: error("loginResponse no configurado")
    }

    override suspend fun refresh(body: RefreshRequest): AuthResponseDto =
        error("no usado en estos tests")

    override suspend fun logout() = Unit

    override suspend fun requestPasswordOtp(body: RequestPasswordOtpRequest) =
        MessageResponseDto("ok")

    override suspend fun resetPasswordWithOtp(body: ResetPasswordOtpRequest) =
        MessageResponseDto("ok")

    override suspend fun me(): com.luki.play.data.auth.api.UserProfileDto =
        error("no usado en estos tests")

    override suspend fun changePassword(body: com.luki.play.data.auth.api.ChangePasswordRequest) =
        MessageResponseDto("ok")
}

private class FakeTokenStore : TokenStore {
    private var access: String? = null
    private var refresh: String? = null
    private var user: String? = null
    private var name: String? = null
    private var mail: String? = null
    private var userPlan: String? = null

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun userId(): String? = user
    override fun displayName(): String? = name
    override fun email(): String? = mail
    override fun plan(): String? = userPlan
    override fun deviceId(): String = "test-device"
    override fun existingDeviceId(): String? = "test-device"
    override fun adoptDeviceId(candidate: String): String = "test-device"

    override fun save(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        displayName: String?,
        email: String?,
        plan: String?,
    ) {
        access = accessToken; refresh = refreshToken; user = userId; name = displayName
        mail = email; userPlan = plan
    }

    override fun updateTokens(accessToken: String, refreshToken: String?) {
        access = accessToken; refresh = refreshToken
    }

    override fun clear() {
        access = null; refresh = null; user = null; name = null; mail = null; userPlan = null
    }
}
