// test/feature/login/PrimerLoginViewModelTest.kt
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Configura tu cuenta" (primer login). Réplica de `PrimerLoginForm` +
 * `VerifyEmailForm`: valida la contraseña con [PasswordPolicy] + coincidencia;
 * si el backend pide verificar el correo pasa al paso VERIFY_EMAIL; si no,
 * termina (done → Home). "Verificar más tarde" también termina.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrimerLoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(requiresEmailVerification: Boolean = false): PrimerLoginViewModel =
        PrimerLoginViewModel(
            AuthRepository(NoopAuthApi(), FakeAccountApi(requiresEmailVerification = requiresEmailVerification), FakeTokenStore(), dispatcher),
            FakeTokenStore(initialDisplayName = "Sofía"),
        )

    @Test
    fun `contrasenas distintas se bloquean sin llamar al backend`() = runTest(dispatcher) {
        val vm = vm()

        vm.completeSetup("NuevaClave123", "OtraClave123", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Las contraseñas no coinciden", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.done)
        assertEquals(PrimerLoginStep.CONFIGURE, vm.uiState.value.step)
    }

    @Test
    fun `sin correo por verificar termina directo en done`() = runTest(dispatcher) {
        val vm = vm(requiresEmailVerification = false)

        vm.completeSetup("NuevaClave123", "NuevaClave123", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `con correo nuevo pasa al paso de verificacion, no a done`() = runTest(dispatcher) {
        val vm = vm(requiresEmailVerification = true)

        vm.completeSetup("NuevaClave123", "NuevaClave123", "nuevo@x.ec")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PrimerLoginStep.VERIFY_EMAIL, vm.uiState.value.step)
        assertFalse(vm.uiState.value.done)
    }

    @Test
    fun `codigo de longitud invalida se bloquea localmente`() = runTest(dispatcher) {
        val vm = vm(requiresEmailVerification = true)
        vm.completeSetup("NuevaClave123", "NuevaClave123", "nuevo@x.ec")
        dispatcher.scheduler.advanceUntilIdle()

        vm.verifyCode("123")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("El código debe tener 6 dígitos", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.done)
    }

    @Test
    fun `codigo valido termina en done`() = runTest(dispatcher) {
        val vm = vm(requiresEmailVerification = true)
        vm.completeSetup("NuevaClave123", "NuevaClave123", "nuevo@x.ec")
        dispatcher.scheduler.advanceUntilIdle()

        vm.verifyCode("123456")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `verificar mas tarde termina en done`() = runTest(dispatcher) {
        val vm = vm(requiresEmailVerification = true)
        vm.completeSetup("NuevaClave123", "NuevaClave123", "nuevo@x.ec")
        dispatcher.scheduler.advanceUntilIdle()

        vm.skip()

        assertTrue(vm.uiState.value.done)
    }
}

private class NoopAuthApi : AuthApi {
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
    override suspend fun submitRegistrationRequest(body: com.luki.play.data.auth.api.RegistrationRequestBody) = error("no usado")
}
