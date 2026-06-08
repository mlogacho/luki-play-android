// data/profiles/ProfilesRepository.kt
package com.luki.play.data.profiles

import android.content.Context
import com.luki.play.data.profiles.api.CreateProfileRequest
import com.luki.play.data.profiles.api.ProfileDto
import com.luki.play.data.profiles.api.ProfilesApi
import com.luki.play.data.profiles.domain.Profile
import com.luki.play.util.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de perfiles + perfil activo.
 *
 * El perfil activo se persiste en EncryptedSharedPreferences porque condiciona
 * el filtro de contenido (kid / parental) — no es metadato banal.
 */
@Singleton
class ProfilesRepository @Inject constructor(
    private val api: ProfilesApi,
    @ApplicationContext private val context: Context,
) {

    private val _activeProfileId = MutableStateFlow(loadActiveProfileId())
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    suspend fun list(): Result<List<Profile>> = withContext(Dispatchers.IO) {
        runCatching { api.list().map { it.toDomain() } }
            .onFailure { Timber.tag(TAG).w(it, "ProfilesRepository.list failed") }
    }

    suspend fun create(name: String, avatarUrl: String?, isKid: Boolean, parentalRating: Int, requiresPin: Boolean): Result<Profile> =
        withContext(Dispatchers.IO) {
            runCatching {
                api.create(CreateProfileRequest(name, avatarUrl, isKid, parentalRating, requiresPin)).toDomain()
            }
        }

    suspend fun delete(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { api.delete(profileId) }
    }

    fun setActive(profileId: String) {
        SecureStorage.prefs(context).edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
        _activeProfileId.value = profileId
    }

    fun clearActive() {
        SecureStorage.prefs(context).edit().remove(KEY_ACTIVE_PROFILE).apply()
        _activeProfileId.value = null
    }

    private fun loadActiveProfileId(): String? =
        SecureStorage.prefs(context).getString(KEY_ACTIVE_PROFILE, null)?.takeIf { it.isNotBlank() }

    private fun ProfileDto.toDomain() = Profile(
        id              = id,
        name            = name,
        avatarUrl       = avatarUrl,
        isKid           = isKid,
        parentalRating  = parentalRating,
        requiresPin     = requiresPin,
    )

    companion object {
        private const val TAG = "ProfilesRepo"
        private const val KEY_ACTIVE_PROFILE = "luki_active_profile"
    }
}
