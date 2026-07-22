// data/auth/SecureTokenStore.kt
package com.luki.play.data.auth

import android.content.Context
import com.luki.play.util.Constants
import com.luki.play.util.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [TokenStore] respaldada por [SecureStorage]
 * (EncryptedSharedPreferences).
 *
 * El `deviceId` se genera la primera vez y se mantiene aunque el usuario
 * cierre sesión: identifica al dispositivo, no a la sesión.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {

    private val prefs by lazy { SecureStorage.prefs(context) }

    override fun accessToken(): String? =
        prefs.getString(Constants.KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun refreshToken(): String? =
        prefs.getString(Constants.KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun userId(): String? =
        prefs.getString(Constants.KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    override fun displayName(): String? =
        prefs.getString(Constants.KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    override fun email(): String? =
        prefs.getString(Constants.KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    override fun plan(): String? =
        prefs.getString(Constants.KEY_PLAN, null)?.takeIf { it.isNotBlank() }

    override fun save(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        displayName: String?,
        email: String?,
        plan: String?,
    ) {
        prefs.edit().apply {
            putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(Constants.KEY_REFRESH_TOKEN, it) }
            userId?.takeIf { it.isNotBlank() }?.let { putString(Constants.KEY_USER_ID, it) }
            displayName?.takeIf { it.isNotBlank() }?.let { putString(Constants.KEY_DISPLAY_NAME, it) }
            email?.takeIf { it.isNotBlank() }?.let { putString(Constants.KEY_EMAIL, it) }
            plan?.takeIf { it.isNotBlank() }?.let { putString(Constants.KEY_PLAN, it) }
            apply()
        }
    }

    override fun updateTokens(accessToken: String, refreshToken: String?) {
        prefs.edit().apply {
            putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(Constants.KEY_REFRESH_TOKEN, it) }
            apply()
        }
    }

    override fun clear() {
        prefs.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .remove(Constants.KEY_USER_ID)
            .remove(Constants.KEY_DISPLAY_NAME)
            .remove(Constants.KEY_EMAIL)
            .remove(Constants.KEY_PLAN)
            .apply()
    }

    override fun deviceId(): String {
        val existing = prefs.getString(Constants.KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(Constants.KEY_DEVICE_ID, generated).apply()
        return generated
    }

    override fun existingDeviceId(): String? =
        prefs.getString(Constants.KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    override fun adoptDeviceId(candidate: String): String {
        val existing = prefs.getString(Constants.KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val trimmed = candidate.trim()
        // Un candidato vacío no puede fijar el id: se cae al generador.
        if (trimmed.isEmpty()) return deviceId()
        prefs.edit().putString(Constants.KEY_DEVICE_ID, trimmed).apply()
        return trimmed
    }
}
