// test/data/auth/AuthRepositoryTest.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.ActivationCodeDto
import com.luki.play.data.auth.api.AuthApi
import com.luki.play.data.auth.api.AuthResponseDto
import com.luki.play.data.auth.api.AuthUserDto
import com.luki.play.data.auth.api.ContractLoginRequest
import com.luki.play.data.auth.api.IdLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.RefreshRequest
import com.luki.play.data.auth.api.RequestPasswordOtpRequest
import com.luki.play.data.auth.api.ResetPasswordOtpRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            // Shape real del backend: user anidado (contract-login.use-case.ts).
            loginResponse = AuthResponseDto(
                accessToken  = "a1",
                refreshToken = "r1",
                user         = AuthUserDto(id = "u1", name = "Sofia", email = "s@x.ec", plan = "lukiplay"),
            )
        )
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, FakeAccountApi(), store)

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
            loginResponse = AuthResponseDto("a", null, AuthUserDto("u", "Nick", null, null))
        )
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, FakeAccountApi(), store)

        repo.loginWithContract("C-99", "pwd").getOrThrow()

        assertEquals(0, api.idLoginCalls)
        assertEquals(1, api.contractLoginCalls)
        assertEquals("C-99", api.lastContractRequest!!.contractNumber)
    }

    @Test
    fun `login fallido devuelve Result failure sin tocar store`() = runTest {
        val api = FakeAuthApi(loginException = IOException("boom"))
        val store = FakeTokenStore()
        val repo  = AuthRepository(api, FakeAccountApi(), store)

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
        val repo = AuthRepository(api, FakeAccountApi(), store)

        val result = repo.logout()

        assertTrue(result.isSuccess)
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        assertEquals(1, store.clearCount)
        assertTrue(repo.current() is SessionState.Anonymous)
    }

    // ── Activación de cuenta ──

    @Test
    fun `firstAccess devuelve el customerId`() = runTest {
        val api = FakeAuthApi(firstAccessCustomerId = "cus_42")
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        assertEquals("cus_42", repo.firstAccess("1753086899").getOrThrow())
    }

    @Test
    fun `firstAccess sin customerId es failure`() = runTest {
        val repo = AuthRepository(FakeAuthApi(firstAccessCustomerId = null), FakeAccountApi(), FakeTokenStore())

        assertTrue(repo.firstAccess("x").isFailure)
    }

    @Test
    fun `requestActivationCode propaga needsSupportCode y solo manda customerId`() = runTest {
        val api = FakeAuthApi(activationCode = ActivationCodeDto(sent = false, needsSupportCode = true))
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        val channel = repo.requestActivationCode("cus_1").getOrThrow()

        assertTrue(channel.needsSupportCode)
        // Garantía de seguridad (P0): el cuerpo NO deja al cliente elegir destino;
        // solo viaja el customerId y el backend envía al correo registrado.
        assertEquals("cus_1", api.lastActivationCodeRequest!!.customerId)
    }

    @Test
    fun `activate crea sesion como el login y normaliza el codigo`() = runTest {
        val api = FakeAuthApi(
            activateResponse = AuthResponseDto(
                accessToken = "acc", refreshToken = "ref",
                user = AuthUserDto("u9", "Nueva", "n@x.ec", "lukiplay"),
            )
        )
        val store = FakeTokenStore()
        val repo = AuthRepository(api, FakeAccountApi(), store)

        val session = repo.activate("cus_1", "a1b2c3", "Secreta123", "n@x.ec").getOrThrow()

        assertEquals("acc", session.accessToken)
        assertEquals("u9", session.userId)
        assertEquals("acc", store.accessToken())
        assertTrue(repo.current() is SessionState.Authenticated)

        val req = api.lastActivateRequest!!
        assertEquals("cus_1", req.customerId)
        assertEquals("A1B2C3", req.otpCode)      // normalizado a mayúsculas
        assertEquals("Secreta123", req.password)
        assertEquals("n@x.ec", req.email)
    }

    @Test
    fun `activate sin correo manda email nulo`() = runTest {
        val api = FakeAuthApi(
            activateResponse = AuthResponseDto("a", "r", AuthUserDto("u", "N", null, null))
        )
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        repo.activate("cus_1", "ABC123", "Secreta123", "").getOrThrow()

        assertNull(api.lastActivateRequest!!.email)
    }

    @Test
    fun `submitRegistrationRequest recorta campos y omite opcionales en blanco`() = runTest {
        val api = FakeAuthApi()
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        repo.submitRegistrationRequest(
            nombres = "  Juan Carlos  ",
            apellidos = "  Pérez López  ",
            idNumber = "  1720345678  ",
            telefono = "  0991234567  ",
            email = "   ",
            direccion = "   ",
        ).getOrThrow()

        val req = api.lastRegistrationRequest!!
        assertEquals("Juan Carlos", req.nombres)
        assertEquals("Pérez López", req.apellidos)
        assertEquals("1720345678", req.idNumber)
        assertEquals("0991234567", req.telefono)
        // Opcionales en blanco → null (no cadena vacía), como el portal.
        assertNull(req.email)
        assertNull(req.direccion)
    }

    @Test
    fun `submitRegistrationRequest pasa los opcionales recortados cuando vienen`() = runTest {
        val api = FakeAuthApi()
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        repo.submitRegistrationRequest(
            nombres = "Ana", apellidos = "Ruiz", idNumber = "1720345678",
            telefono = "0990000000", email = "  ana@x.ec ", direccion = " Quito ",
        ).getOrThrow()

        val req = api.lastRegistrationRequest!!
        assertEquals("ana@x.ec", req.email)
        assertEquals("Quito", req.direccion)
    }

    @Test
    fun `submitRegistrationRequest propaga el fallo del backend como Result_failure`() = runTest {
        val api = FakeAuthApi(registrationException = RuntimeException("409"))
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        val result = repo.submitRegistrationRequest("Ana", "Ruiz", "1720345678", "0990000000", null, null)

        assertTrue(result.isFailure)
    }

    @Test
    fun `login con clave temporal marca requiresPrimerLogin`() = runTest {
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto(
                accessToken = "a", refreshToken = "r",
                user = AuthUserDto("u1", "Temp", null, "lukiplay"),
                isTempPassword = true,
            )
        )
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        val session = repo.loginWithId("1720345678", "temporal").getOrThrow()

        assertTrue(session.requiresPrimerLogin)
    }

    @Test
    fun `login normal no exige primer login`() = runTest {
        val api = FakeAuthApi(
            loginResponse = AuthResponseDto("a", "r", AuthUserDto("u1", "N", null, "p"))
        )
        val repo = AuthRepository(api, FakeAccountApi(), FakeTokenStore())

        val session = repo.loginWithId("1720345678", "clave").getOrThrow()

        assertFalse(session.requiresPrimerLogin)
    }

    @Test
    fun `completePrimerLogin devuelve si falta verificar el correo`() = runTest {
        val repo = AuthRepository(
            FakeAuthApi(), FakeAccountApi(requiresEmailVerification = true), FakeTokenStore(),
        )

        val requiresEmail = repo.completePrimerLogin("NuevaClave123", "NuevaClave123", "ana@x.ec").getOrThrow()

        assertTrue(requiresEmail)
    }

    @Test
    fun `verifyEmail exitoso devuelve Result_success`() = runTest {
        val repo = AuthRepository(FakeAuthApi(), FakeAccountApi(), FakeTokenStore())

        val result = repo.verifyEmail("123456")

        assertTrue(result.isSuccess)
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
    private val firstAccessCustomerId: String? = null,
    private val activationCode: com.luki.play.data.auth.api.ActivationCodeDto? = null,
    private val activateResponse: AuthResponseDto? = null,
    private val registrationException: Throwable? = null,
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
    var lastActivationCodeRequest: com.luki.play.data.auth.api.RequestActivationCodeRequest? = null
        private set
    var lastActivateRequest: com.luki.play.data.auth.api.ActivateRequest? = null
        private set
    var lastRegistrationRequest: com.luki.play.data.auth.api.RegistrationRequestBody? = null
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

    override suspend fun requestPasswordOtp(body: RequestPasswordOtpRequest) =
        MessageResponseDto("Si la cédula existe, enviamos un código.")

    override suspend fun resetPasswordWithOtp(body: ResetPasswordOtpRequest) =
        MessageResponseDto("Contraseña actualizada.")

    override suspend fun firstAccess(body: com.luki.play.data.auth.api.FirstAccessRequest) =
        com.luki.play.data.auth.api.FirstAccessDto(firstAccessCustomerId)

    override suspend fun requestActivationCode(body: com.luki.play.data.auth.api.RequestActivationCodeRequest): com.luki.play.data.auth.api.ActivationCodeDto {
        lastActivationCodeRequest = body
        return activationCode ?: com.luki.play.data.auth.api.ActivationCodeDto(sent = true)
    }

    override suspend fun verifyActivationCode(body: com.luki.play.data.auth.api.VerifyActivationCodeRequest) =
        MessageResponseDto("ok")

    override suspend fun activate(body: com.luki.play.data.auth.api.ActivateRequest): AuthResponseDto {
        lastActivateRequest = body
        return activateResponse ?: error("FakeAuthApi: activateResponse no configurado")
    }

    override suspend fun submitRegistrationRequest(body: com.luki.play.data.auth.api.RegistrationRequestBody): MessageResponseDto {
        lastRegistrationRequest = body
        registrationException?.let { throw it }
        return MessageResponseDto("Tu solicitud ha sido enviada. Te contactaremos pronto.")
    }
}
