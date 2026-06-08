// data/parental/ParentalControl.kt
package com.luki.play.data.parental

import android.content.Context
import com.luki.play.util.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Control parental local: gestiona el PIN del titular de la cuenta.
 *
 * Almacenamiento:
 *  - El PIN nunca se guarda en plano. Se almacena `salt + sha256(salt||pin)`
 *    en [SecureStorage] (EncryptedSharedPreferences).
 *  - El salt es por-dispositivo y se regenera al cambiar el PIN.
 *
 * Operaciones:
 *  - [setPin]: establece o cambia el PIN (4–6 dígitos).
 *  - [verify]: compara un intento con el hash guardado.
 *  - [clear]: borra el PIN (perfil sin parental).
 *
 * No depende de red — la política contractual con el servidor se valida
 * separadamente; este objeto sólo gestiona la fricción local.
 */
@Singleton
class ParentalControl @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy { SecureStorage.prefs(context) }

    fun hasPin(): Boolean = !prefs.getString(KEY_HASH, null).isNullOrBlank()

    fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) {
            "PIN debe tener entre 4 y 6 dígitos"
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(salt, pin)
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash.toHex())
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expectedHex = prefs.getString(KEY_HASH, null) ?: return false
        val actualHex = hash(saltHex.fromHex(), pin).toHex()
        return constantTimeEquals(actualHex, expectedHex)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .apply()
    }

    // ── Hash helpers ────────────────────────────────────────────────────────

    private fun hash(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { b -> "%02x".format(b) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "hex inválido" }
        return ByteArray(length / 2) { i ->
            ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
        }
    }

    /** Comparación tiempo-constante para evitar timing attacks sobre el PIN. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        private const val KEY_SALT = "luki_parental_salt"
        private const val KEY_HASH = "luki_parental_hash"
        private const val SALT_BYTES = 16
    }
}
