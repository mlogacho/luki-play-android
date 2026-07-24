// test/data/auth/FakeAccountApi.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AccountApi
import com.luki.play.data.auth.api.ChangePasswordRequest
import com.luki.play.data.auth.api.CompletePrimerLoginRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.PrimerLoginResultDto
import com.luki.play.data.auth.api.SendEmailVerificationRequest
import com.luki.play.data.auth.api.UserProfileDto
import com.luki.play.data.auth.api.VerifyEmailRequest

/**
 * [AccountApi] de prueba. Los tests actuales de auth ejercitan login/refresh/
 * logout, no `me`/`changePassword`, así que por defecto fallan si se llaman.
 */
class FakeAccountApi(
    private val profile: UserProfileDto? = null,
    private val requiresEmailVerification: Boolean = false,
) : AccountApi {
    override suspend fun me(): UserProfileDto =
        profile ?: error("FakeAccountApi: me no configurado")

    override suspend fun changePassword(body: ChangePasswordRequest): MessageResponseDto =
        MessageResponseDto("Contraseña actualizada.")

    override suspend fun completePrimerLogin(body: CompletePrimerLoginRequest): PrimerLoginResultDto =
        PrimerLoginResultDto("Contraseña actualizada.", requiresEmailVerification)

    override suspend fun sendEmailVerification(body: SendEmailVerificationRequest): MessageResponseDto =
        MessageResponseDto("Código enviado.")

    override suspend fun verifyEmail(body: VerifyEmailRequest): MessageResponseDto =
        MessageResponseDto("Correo verificado.")
}
