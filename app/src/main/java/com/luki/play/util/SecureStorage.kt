// util/SecureStorage.kt
package com.luki.play.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * Almacenamiento seguro de credenciales mediante [EncryptedSharedPreferences].
 *
 * Las claves y valores quedan cifrados en disco con AES-256-GCM (valores)
 * y AES-256-SIV (claves), respaldados por Android Keystore.
 *
 * Incluye migración one-shot: si detecta tokens en las prefs planas legacy
 * ("luki_prefs"), los copia al almacén cifrado y borra el original.
 */
object SecureStorage {

    private const val ENCRYPTED_PREFS_NAME = "luki_secure_prefs"
    private const val LEGACY_PREFS_NAME    = Constants.PREFS_NAME

    @Volatile private var cached: SharedPreferences? = null

    /**
     * Retorna la instancia de [SharedPreferences] cifrada, memoizada: crearla
     * implica IPC al Keystore + descifrado de keysets, y la creación concurrente
     * desde varios hilos (bridge JS, OkHttp, main) puede corromper el keyset.
     * La primera llamada crea el almacén si no existe y ejecuta la migración.
     */
    fun prefs(context: Context): SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: create(context.applicationContext).also { cached = it }
        }

    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encrypted = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyPrefsIfNeeded(context, encrypted)
        return encrypted
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Claves que nunca deben permanecer en el fichero plano legacy. */
    private val SENSITIVE_KEYS = listOf(
        Constants.KEY_ACCESS_TOKEN,
        Constants.KEY_REFRESH_TOKEN,
        Constants.KEY_USER_ID,
        Constants.KEY_DISPLAY_NAME,
    )

    /**
     * Si las prefs legacy contienen tokens, los copia al almacén cifrado
     * (solo cuando este aún no tiene refresh token, para no pisar una sesión
     * más nueva) y siempre elimina las entradas sensibles del fichero plano:
     * no deben quedar en claro aunque la copia se haya saltado.
     */
    private fun migrateLegacyPrefsIfNeeded(
        context: Context,
        encrypted: SharedPreferences
    ) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (SENSITIVE_KEYS.none { legacy.contains(it) }) return

        val legacyRefresh = legacy.getString(Constants.KEY_REFRESH_TOKEN, null)
        if (!encrypted.contains(Constants.KEY_REFRESH_TOKEN) && !legacyRefresh.isNullOrBlank()) {
            Timber.i("SecureStorage: migrando tokens legacy → EncryptedSharedPreferences")
            encrypted.edit().apply {
                SENSITIVE_KEYS.forEach { copyString(legacy, this, it) }
                apply()
            }
        }

        // Borra solo las claves sensibles; deja el resto de legacy intacto
        legacy.edit().apply {
            SENSITIVE_KEYS.forEach { remove(it) }
            apply()
        }

        Timber.i("SecureStorage: prefs legacy limpiadas")
    }

    private fun copyString(
        src: SharedPreferences,
        dst: SharedPreferences.Editor,
        key: String
    ) {
        src.getString(key, null)?.let { dst.putString(key, it) }
    }
}
