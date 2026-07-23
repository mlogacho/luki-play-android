// test/data/auth/api/UserProfileDtoTest.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ancla el contrato de `GET /auth/me` al shape REAL del backend
 * (`UserProfileResponse` en `frontend/services/api/authApi.ts`).
 *
 * Igual que [AuthResponseDtoTest]/`StreamDtoTest`: si el backend renombra un
 * campo, la pantalla de perfil mostraría "—" en silencio. Este test lo cambia
 * por un fallo antes de salir a producción.
 */
class UserProfileDtoTest {

    private val adapter = Moshi.Builder().build().adapter(UserProfileDto::class.java)

    @Test
    fun `parsea el perfil completo de auth me`() {
        // Copia literal del shape de UserProfileResponse (authApi.ts:61-79),
        // con los campos extra que el backend también manda (role, permissions…)
        // para verificar que Moshi los ignora sin fallar.
        val json = """
            {
              "id": "cus_ab12cd34ef",
              "firstName": "Sofía",
              "lastName": "Soria",
              "idNumber": "1753086899",
              "contractNumber": "C-00042",
              "email": "sofia@ejemplo.ec",
              "role": "customer",
              "status": "active",
              "accountId": "acc_1",
              "contractType": "residencial",
              "serviceStatus": "ACTIVE",
              "canAccessOtt": true,
              "restrictionMessage": null,
              "lastLoginAt": "2026-07-22T14:30:00.000Z",
              "mustChangePassword": false,
              "permissions": ["ott.view"],
              "entitlements": ["lukiplay"]
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("cus_ab12cd34ef", dto.id)
        assertEquals("Sofía", dto.firstName)
        assertEquals("Soria", dto.lastName)
        assertEquals("1753086899", dto.idNumber)
        assertEquals("C-00042", dto.contractNumber)
        assertEquals("sofia@ejemplo.ec", dto.email)
        assertEquals("ACTIVE", dto.serviceStatus)
        assertTrue(dto.canAccessOtt)
        assertEquals("2026-07-22T14:30:00.000Z", dto.lastLoginAt)
    }

    @Test
    fun `tolera campos nulos sin reventar`() {
        // El backend permite idNumber/contractNumber/serviceStatus/lastLoginAt
        // en null (UserProfileResponse los declara nullable).
        val json = """
            {
              "id": "cus_x",
              "firstName": "Ana",
              "lastName": "",
              "idNumber": null,
              "contractNumber": null,
              "email": "ana@ejemplo.ec",
              "serviceStatus": null,
              "canAccessOtt": false,
              "lastLoginAt": null
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertNull(dto.idNumber)
        assertNull(dto.contractNumber)
        assertNull(dto.serviceStatus)
        assertNull(dto.lastLoginAt)
        assertFalse(dto.canAccessOtt)
    }

    @Test
    fun `canAccessOtt ausente NO restringe por defecto`() {
        // Ante la duda no bloqueamos el acceso: el default es true.
        val dto = adapter.fromJson("""{"id":"cus_y","email":"y@ejemplo.ec"}""")!!

        assertTrue(dto.canAccessOtt)
        assertEquals("", dto.firstName.orEmpty())
    }
}
