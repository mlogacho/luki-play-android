// data/auth/api/AuthApi.kt
package com.luki.play.data.auth.api

import com.luki.play.util.Constants
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Endpoints de autenticación de Luki Play.
 *
 * Las peticiones de [refresh] se invocan desde [TokenAuthenticator] con un
 * cliente OkHttp separado para evitar bucles con el interceptor de Bearer.
 */
interface AuthApi {

    @POST(Constants.Auth.LOGIN_ID)
    suspend fun loginWithId(@Body body: IdLoginRequest): AuthResponseDto

    @POST(Constants.Auth.LOGIN_CONTRACT)
    suspend fun loginWithContract(@Body body: ContractLoginRequest): AuthResponseDto

    @POST(Constants.Auth.REFRESH)
    suspend fun refresh(@Body body: RefreshRequest): AuthResponseDto

    @POST(Constants.Auth.LOGOUT)
    suspend fun logout(): Unit

    @POST(Constants.Auth.REQUEST_OTP)
    suspend fun requestPasswordOtp(@Body body: RequestPasswordOtpRequest): MessageResponseDto

    @POST(Constants.Auth.RESET_WITH_OTP)
    suspend fun resetPasswordWithOtp(@Body body: ResetPasswordOtpRequest): MessageResponseDto

    // ── Activación de cuenta (sin auth) ──

    @POST(Constants.Auth.FIRST_ACCESS)
    suspend fun firstAccess(@Body body: FirstAccessRequest): FirstAccessDto

    @POST(Constants.Auth.REQUEST_ACTIVATION_CODE)
    suspend fun requestActivationCode(@Body body: RequestActivationCodeRequest): ActivationCodeDto

    @POST(Constants.Auth.VERIFY_ACTIVATION_CODE)
    suspend fun verifyActivationCode(@Body body: VerifyActivationCodeRequest): MessageResponseDto

    @POST(Constants.Auth.ACTIVATE)
    suspend fun activate(@Body body: ActivateRequest): AuthResponseDto

    /** Solicitud de acceso de un no-cliente (captación de lead, sin auth). */
    @POST(Constants.Auth.REGISTRATION_REQUEST)
    suspend fun submitRegistrationRequest(@Body body: RegistrationRequestBody): MessageResponseDto
}
