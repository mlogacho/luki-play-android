// test/data/auth/api/AuthResponseDtoTest.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ancla el contrato de deserialización al shape REAL del backend
 * (Luki-Play-OTT: contract-login.use-case.ts / auth/refresh, 2026-07-21).
 *
 * Si el backend cambia el shape (o alguien "arregla" el DTO a los alias
 * planos legacy), estos tests fallan antes de que el login nativo salga
 * a producción con userId/displayName vacíos.
 */
class AuthResponseDtoTest {

    private val adapter = Moshi.Builder().build().adapter(AuthResponseDto::class.java)

    @Test
    fun `parsea la respuesta real de login con user anidado`() {
        // Copia literal del shape de contract-login.use-case.ts:120-128.
        val json = """
            {
              "accessToken": "jwt-access",
              "refreshToken": "jwt-refresh",
              "user": {
                "id": "cus_123",
                "name": "Carlos Paz",
                "email": "carlos@ejemplo.ec",
                "plan": "lukiplay"
              }
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("jwt-access", dto.accessToken)
        assertEquals("jwt-refresh", dto.refreshToken)
        assertEquals("cus_123", dto.resolvedUserId())
        assertEquals("Carlos Paz", dto.resolvedDisplayName())
    }

    @Test
    fun `parsea la respuesta de refresh sin user y con campos extra`() {
        // /auth/refresh devuelve canAccessOtt y restrictionMessage; Moshi debe
        // ignorarlos sin fallar y la identidad queda vacía (no viene user).
        val json = """
            {
              "accessToken": "jwt-nuevo",
              "refreshToken": "jwt-rotado",
              "canAccessOtt": true,
              "restrictionMessage": null
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("jwt-nuevo", dto.accessToken)
        assertEquals("jwt-rotado", dto.refreshToken)
        assertNull(dto.user)
        assertEquals("", dto.resolvedUserId())
        assertEquals("", dto.resolvedDisplayName())
    }

    @Test
    fun `tolera el shape legacy plano como fallback`() {
        val json = """{"accessToken":"a","refreshToken":null,"userId":"u9","nombre":"Ana"}"""

        val dto = adapter.fromJson(json)!!

        assertEquals("u9", dto.resolvedUserId())
        assertEquals("Ana", dto.resolvedDisplayName())
    }

    @Test
    fun `parsea las banderas de primer login del nivel superior`() {
        // Shape real de id-number-login.use-case.ts:192-205: las banderas van
        // junto a accessToken, NO dentro de user.
        val json = """
            {
              "accessToken": "a",
              "refreshToken": "r",
              "mustChangePassword": true,
              "primerLogin": true,
              "isTempPassword": true,
              "emailVerificado": false,
              "user": {"id":"c1","name":"Nuevo","email":"","plan":"lukiplay"}
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals(true, dto.primerLogin)
        assertEquals(true, dto.isTempPassword)
        // El disparador del portal (login.tsx:105): isTempPassword || primerLogin.
        assertEquals(true, dto.requiresPrimerLogin())
    }

    @Test
    fun `sin banderas de primer login el flujo normal no las exige`() {
        val json = """{"accessToken":"a","refreshToken":"r","user":{"id":"c1","name":"N","email":"","plan":"p"}}"""

        val dto = adapter.fromJson(json)!!

        // Ausentes → default false → no se exige primer login.
        assertEquals(false, dto.requiresPrimerLogin())
    }

    @Test
    fun `user anidado tiene prioridad sobre alias planos`() {
        val json = """
            {"accessToken":"a","refreshToken":"r",
             "user":{"id":"nested","name":"Nested","email":null,"plan":null},
             "userId":"flat","nombre":"Flat"}
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("nested", dto.resolvedUserId())
        assertEquals("Nested", dto.resolvedDisplayName())
    }
}
