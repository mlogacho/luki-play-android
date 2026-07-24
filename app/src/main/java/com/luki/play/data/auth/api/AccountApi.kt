// data/auth/api/AccountApi.kt
package com.luki.play.data.auth.api

import com.luki.play.util.Constants
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Endpoints de CUENTA que exigen Bearer.
 *
 * Van aparte de [AuthApi] a propósito: [AuthApi] se provee con el cliente
 * "plain" (sin [com.luki.play.data.network.AuthInterceptor]) porque login y
 * refresh no deben llevar token —y el refresh lo usa el TokenAuthenticator sin
 * recursión—. `me`/`changePassword` sí necesitan el Bearer, así que se
 * inyectan desde el cliente autenticado. Ponerlos en AuthApi hacía que
 * salieran sin token y el backend respondía 401.
 */
interface AccountApi {

    @GET(Constants.Auth.ME)
    suspend fun me(): UserProfileDto

    @POST(Constants.Auth.CHANGE_PASSWORD)
    suspend fun changePassword(@Body body: ChangePasswordRequest): MessageResponseDto

    // ── Primer login + verificación de correo (requieren el Bearer del login) ──

    @POST(Constants.Auth.COMPLETE_PRIMER_LOGIN)
    suspend fun completePrimerLogin(@Body body: CompletePrimerLoginRequest): PrimerLoginResultDto

    @POST(Constants.Auth.SEND_EMAIL_VERIFICATION)
    suspend fun sendEmailVerification(@Body body: SendEmailVerificationRequest): MessageResponseDto

    @POST(Constants.Auth.VERIFY_EMAIL)
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): MessageResponseDto
}
