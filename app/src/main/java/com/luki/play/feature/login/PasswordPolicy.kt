// feature/login/PasswordPolicy.kt
package com.luki.play.feature.login

/**
 * Política de contraseña unificada — réplica exacta de
 * `frontend/services/passwordPolicy.ts` del portal, que a su vez coincide
 * con la validación del backend (`ResetPasswordOtpDto`): mínimo 8
 * caracteres, al menos una minúscula, una mayúscula y un número.
 *
 * Mantener los mensajes IDÉNTICOS a los del front: el usuario puede pasar
 * de la web a la app y debe recibir la misma guía.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8
    const val RULE_HINT = "Mín. 8 caracteres, mayúscula, minúscula y número"

    /** @return mensaje de error si es inválida, o `null` si cumple. */
    fun validate(password: String): String? = when {
        password.isEmpty()                 -> "La contraseña es requerida"
        password.length < MIN_LENGTH       -> "La contraseña debe tener al menos $MIN_LENGTH caracteres"
        !password.any { it in 'a'..'z' }   -> "La contraseña debe incluir al menos una minúscula"
        !password.any { it in 'A'..'Z' }   -> "La contraseña debe incluir al menos una mayúscula"
        !password.any { it.isDigit() }     -> "La contraseña debe incluir al menos un número"
        else                               -> null
    }
}
