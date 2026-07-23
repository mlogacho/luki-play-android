// data/auth/api/AuthApi.kt
package com.luki.play.data.auth.api

import com.luki.play.util.Constants
import retrofit2.http.Body
import retrofit2.http.GET
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

    /** Perfil del usuario autenticado. El Bearer lo pone [AuthInterceptor]. */
    @GET(Constants.Auth.ME)
    suspend fun me(): UserProfileDto

    /** Cambia la contraseña; el backend revoca todas las sesiones al lograrlo. */
    @POST(Constants.Auth.CHANGE_PASSWORD)
    suspend fun changePassword(@Body body: ChangePasswordRequest): MessageResponseDto
}
