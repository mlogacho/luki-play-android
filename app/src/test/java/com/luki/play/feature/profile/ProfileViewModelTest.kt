// test/feature/profile/ProfileViewModelTest.kt
package com.luki.play.feature.profile

import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.FakeAccountApi
import com.luki.play.data.auth.FakeTokenStore
import com.luki.play.data.auth.api.AccountApi
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.ChangePasswordRequest
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.RefreshRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import com.luki.play.data.auth.api.UserProfileDto
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
 * Ancla el comportamiento del cambio de contraseña: como el backend revoca TODAS
 * las sesiones en éxito, el cierre de sesión forzado NO puede depender de que la
 * hoja siga en composición (una corrutina con scope de UI se cancelaría al
 * cerrarla). El [ProfileViewModel] lo dispara desde su propio scope tras la
 * ventana de éxito, así que ocurre sí o sí; y un fallo no debe cerrar sesión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(account: AccountApi): ProfileViewModel =
        ProfileViewModel(
            AuthRepository(NoopAuthApi(), account, FakeTokenStore(), dispatcher),
            FakeTokenStore(initialDisplayName = "Sofía"),
        )

    @Test
    fun `cambio exitoso fuerza el logout aunque la hoja no siga viva`() = runTest(dispatcher) {
        val vm = vm(FakeAccountApi())   // changePassword devuelve OK
        var success = false
        var forcedLogout = false
        var error: String? = null

        vm.changePassword(
            current = "old",
            next = "NuevaClave123",
            onSuccess = { success = true },
            onForcedLogout = { forcedLogout = true },
            onError = { error = it },
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("se muestra el éxito", success)
        assertTrue("el logout forzado debe dispararse desde el ViewModel", forcedLogout)
        assertNull(error)
    }

    @Test
    fun `un cambio fallido no cierra la sesion`() = runTest(dispatcher) {
        val vm = vm(ThrowingAccountApi(IOException("timeout")))
        var success = false
        var forcedLogout = false
        var error: String? = null

        vm.changePassword(
            current = "old",
            next = "NuevaClave123",
            onSuccess = { success = true },
            onForcedLogout = { forcedLogout = true },
            onError = { error = it },
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(success)
        assertFalse("un cambio fallido no debe cerrar sesión", forcedLogout)
        assertEquals("Sin conexión. Verifica tu internet e intenta de nuevo.", error)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

private class ThrowingAccountApi(private val error: Throwable) : AccountApi {
    override suspend fun me(): UserProfileDto = throw error
    override suspend fun changePassword(body: ChangePasswordRequest): MessageResponseDto = throw error
    override suspend fun completePrimerLogin(body: com.luki.play.data.auth.api.CompletePrimerLoginRequest) = throw error
    override suspend fun sendEmailVerification(body: com.luki.play.data.auth.api.SendEmailVerificationRequest) = throw error
    override suspend fun verifyEmail(body: com.luki.play.data.auth.api.VerifyEmailRequest) = throw error
}

private class NoopAuthApi : AuthApi {
    override suspend fun loginWithId(body: IdLoginRequest): AuthResponseDto = error("no usado")
    override suspend fun loginWithContract(body: ContractLoginRequest): AuthResponseDto = error("no usado")
    override suspend fun refresh(body: RefreshRequest): AuthResponseDto = error("no usado")
    override suspend fun logout() = Unit
    override suspend fun requestPasswordOtp(body: RequestPasswordOtpRequest): MessageResponseDto = error("no usado")
    override suspend fun resetPasswordWithOtp(body: ResetPasswordOtpRequest): MessageResponseDto = error("no usado")
    override suspend fun firstAccess(body: com.luki.play.data.auth.api.FirstAccessRequest) = error("no usado")
    override suspend fun requestActivationCode(body: com.luki.play.data.auth.api.RequestActivationCodeRequest) = error("no usado")
    override suspend fun verifyActivationCode(body: com.luki.play.data.auth.api.VerifyActivationCodeRequest) = error("no usado")
    override suspend fun activate(body: com.luki.play.data.auth.api.ActivateRequest) = error("no usado")
    override suspend fun submitRegistrationRequest(body: com.luki.play.data.auth.api.RegistrationRequestBody) = error("no usado")
}
