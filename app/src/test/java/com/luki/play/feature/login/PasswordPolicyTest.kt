// test/feature/login/PasswordPolicyTest.kt
package com.luki.play.feature.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La política debe coincidir EXACTAMENTE con la del portal
 * (`frontend/services/passwordPolicy.ts`) y con el regex del backend
 * (`ResetPasswordOtpDto`): `^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,}$`.
 *
 * Si divergen, el usuario ve "válida" en la app y el backend la rechaza.
 */
class PasswordPolicyTest {

    @Test
    fun `acepta una contrasena que cumple la regla`() {
        assertNull(PasswordPolicy.validate("NuevaPassword1"))
    }

    @Test
    fun `rechaza vacia`() {
        assertEquals("La contraseña es requerida", PasswordPolicy.validate(""))
    }

    @Test
    fun `rechaza menos de 8 caracteres`() {
        assertEquals(
            "La contraseña debe tener al menos 8 caracteres",
            PasswordPolicy.validate("Abc123"),
        )
    }

    @Test
    fun `rechaza sin minuscula`() {
        assertEquals(
            "La contraseña debe incluir al menos una minúscula",
            PasswordPolicy.validate("PASSWORD1"),
        )
    }

    @Test
    fun `rechaza sin mayuscula`() {
        assertEquals(
            "La contraseña debe incluir al menos una mayúscula",
            PasswordPolicy.validate("password1"),
        )
    }

    @Test
    fun `rechaza sin numero`() {
        assertEquals(
            "La contraseña debe incluir al menos un número",
            PasswordPolicy.validate("PasswordAbc"),
        )
    }

    @Test
    fun `coincide con el regex del backend en un barrido de casos`() {
        val backendRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,}$")
        val casos = listOf(
            "NuevaPassword1", "abcdefgH9", "Ab1", "", "  Aa1bbbbb",
            "TODOMAYUS123", "todominus123", "SinNumeros", "Aa1aaaaa",
            "ñÑ1ñññññ", "P@ssw0rd!",
        )
        casos.forEach { pwd ->
            val localOk = PasswordPolicy.validate(pwd) == null
            val backendOk = backendRegex.matches(pwd)
            assertEquals("desacuerdo para «$pwd»", backendOk, localOk)
        }
    }
}
