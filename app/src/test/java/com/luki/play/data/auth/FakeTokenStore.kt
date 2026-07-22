// test/data/auth/FakeTokenStore.kt
package com.luki.play.data.auth

/**
 * Implementación en-memoria de [TokenStore] para tests unitarios.
 *
 * No depende de Android ni de EncryptedSharedPreferences, por lo que puede
 * usarse en `src/test` (Robolectric no requerido).
 */
class FakeTokenStore(
    initialAccess: String? = null,
    initialRefresh: String? = null,
    initialUserId: String? = null,
    initialDisplayName: String? = null,
    private val fixedDeviceId: String = "device-test",
) : TokenStore {

    @Volatile var access: String? = initialAccess
        private set
    @Volatile var refresh: String? = initialRefresh
        private set
    @Volatile var user: String? = initialUserId
        private set
    @Volatile var name: String? = initialDisplayName
        private set
    @Volatile var mail: String? = null
        private set
    @Volatile var userPlan: String? = null
        private set

    var saveCount = 0
        private set
    var updateCount = 0
        private set
    var clearCount = 0
        private set

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun userId(): String? = user
    override fun displayName(): String? = name
    override fun email(): String? = mail
    override fun plan(): String? = userPlan
    override fun deviceId(): String = fixedDeviceId

    /** El id es fijo en el fake: siempre "existe", así que nunca adopta. */
    override fun adoptDeviceId(candidate: String): String = fixedDeviceId

    override fun save(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        displayName: String?,
        email: String?,
        plan: String?,
    ) {
        access = accessToken
        refresh = refreshToken ?: refresh
        user = userId ?: user
        name = displayName ?: name
        mail = email ?: mail
        userPlan = plan ?: userPlan
        saveCount++
    }

    override fun updateTokens(accessToken: String, refreshToken: String?) {
        access = accessToken
        refresh = refreshToken ?: refresh
        updateCount++
    }

    override fun clear() {
        access = null
        refresh = null
        user = null
        name = null
        mail = null
        userPlan = null
        clearCount++
    }
}
