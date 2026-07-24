// test/feature/login/RegisterRequestViewModelTest.kt
package com.luki.play.feature.login

import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.FakeAccountApi
import com.luki.play.data.auth.FakeTokenStore
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.RefreshRequest
import com.luki.play.data.auth.api.RegistrationRequestBody
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
 * "Solicitar acceso" nativo. La validación local replica la del portal
 * (`RegisterRequestForm`): presencia + cédula ≥ 10 dígitos, SIN checksum; el
 * resto (cédula/RUC real, correo, 409 de cliente existente) lo valida el backend
 * y su mensaje sube tal cual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: FakeRegApi): RegisterRequestViewModel =
        RegisterRequestViewModel(AuthRepository(api, FakeAccountApi(), FakeTokenStore(), dispatcher))

    @Test
    fun `nombre vacio muestra el mensaje del portal sin llamar al backend`() = runTest(dispatcher) {
        val api = FakeRegApi()
        val vm = vm(api)

        vm.submit(" ", "Pérez", "1720345678", "0991234567", "", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("El nombre es requerido", vm.uiState.value.errorMessage)
        assertEquals(0, api.calls)
    }

    @Test
    fun `cedula corta se bloquea localmente`() = runTest(dispatcher) {
        val api = FakeRegApi()
        val vm = vm(api)

        vm.submit("Juan", "Pérez", "12345", "0991234567", "", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("La cédula debe tener al menos 10 dígitos", vm.uiState.value.errorMessage)
        assertEquals(0, api.calls)
    }

    @Test
    fun `solicitud valida termina en done`() = runTest(dispatcher) {
        val api = FakeRegApi()
        val vm = vm(api)

        vm.submit("Juan", "Pérez", "1720345678", "0991234567", "", "")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.done)
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertEquals(1, api.calls)
    }

    @Test
    fun `fallo de red muestra el mensaje de sin conexion`() = runTest(dispatcher) {
        val api = FakeRegApi(failWith = IOException("timeout"))
        val vm = vm(api)

        vm.submit("Juan", "Pérez", "1720345678", "0991234567", "", "")
        dispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertFalse(s.done)
        assertEquals("Sin conexión. Verifica tu internet e intenta de nuevo.", s.errorMessage)
    }
}

private class FakeRegApi(private val failWith: Throwable? = null) : AuthApi {
    var calls = 0; private set

    override suspend fun submitRegistrationRequest(body: RegistrationRequestBody): MessageResponseDto {
        calls++
        failWith?.let { throw it }
        return MessageResponseDto("Tu solicitud ha sido enviada. Te contactaremos pronto.")
    }

    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto = error("no usado")
    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto = error("no usado")
    override suspend fun refresh(body: RefreshRequest): AuthResponseDto = error("no usado")
    override suspend fun logout() = error("no usado")
    override suspend fun requestPasswordOtp(body: RequestPasswordOtpRequest): MessageResponseDto = error("no usado")
    override suspend fun resetPasswordWithOtp(body: ResetPasswordOtpRequest): MessageResponseDto = error("no usado")
    override suspend fun firstAccess(body: com.luki.play.data.auth.api.FirstAccessRequest) = error("no usado")
    override suspend fun requestActivationCode(body: com.luki.play.data.auth.api.RequestActivationCodeRequest) = error("no usado")
    override suspend fun verifyActivationCode(body: com.luki.play.data.auth.api.VerifyActivationCodeRequest) = error("no usado")
    override suspend fun activate(body: com.luki.play.data.auth.api.ActivateRequest) = error("no usado")
}
