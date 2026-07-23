// test/data/auth/FakeAccountApi.kt
package com.luki.play.data.auth

import com.luki.play.data.auth.api.AccountApi
import com.luki.play.data.auth.api.ChangePasswordRequest
import com.luki.play.data.auth.api.MessageResponseDto
import com.luki.play.data.auth.api.UserProfileDto

/**
 * [AccountApi] de prueba. Los tests actuales de auth ejercitan login/refresh/
 * logout, no `me`/`changePassword`, así que por defecto fallan si se llaman.
 */
class FakeAccountApi(
    private val profile: UserProfileDto? = null,
) : AccountApi {
    override suspend fun me(): UserProfileDto =
        profile ?: error("FakeAccountApi: me no configurado")

    override suspend fun changePassword(body: ChangePasswordRequest): MessageResponseDto =
        MessageResponseDto("Contraseña actualizada.")
}
