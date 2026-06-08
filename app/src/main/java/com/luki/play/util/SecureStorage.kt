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

    /**
     * Retorna la instancia de [SharedPreferences] cifrada.
     * La primera llamada crea el almacén si no existe y ejecuta la migración.
     */
    fun prefs(context: Context): SharedPreferences {
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

    /**
     * Si las prefs legacy contienen algún token, los mueve al almacén cifrado
     * y luego elimina las entradas sensibles del fichero plano.
     * Se ejecuta una sola vez gracias a que borra el flag de migración al terminar.
     */
    private fun migrateLegacyPrefsIfNeeded(
        context: Context,
        encrypted: SharedPreferences
    ) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

        // Si el almacén cifrado ya tiene tokens, la migración ya fue hecha
        if (encrypted.contains(Constants.KEY_REFRESH_TOKEN)) return

        val legacyRefresh = legacy.getString(Constants.KEY_REFRESH_TOKEN, null)
        if (legacyRefresh.isNullOrBlank()) return

        Timber.i("SecureStorage: migrando tokens legacy → EncryptedSharedPreferences")

        encrypted.edit().apply {
            copyString(legacy, this, Constants.KEY_ACCESS_TOKEN)
            copyString(legacy, this, Constants.KEY_REFRESH_TOKEN)
            copyString(legacy, this, Constants.KEY_USER_ID)
            copyString(legacy, this, Constants.KEY_DISPLAY_NAME)
            apply()
        }

        // Borra solo las claves sensibles; deja el resto de legacy intacto
        legacy.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .remove(Constants.KEY_USER_ID)
            .remove(Constants.KEY_DISPLAY_NAME)
            .apply()

        Timber.i("SecureStorage: migración completada, prefs legacy limpiadas")
    }

    private fun copyString(
        src: SharedPreferences,
        dst: SharedPreferences.Editor,
        key: String
    ) {
        src.getString(key, null)?.let { dst.putString(key, it) }
    }
}
