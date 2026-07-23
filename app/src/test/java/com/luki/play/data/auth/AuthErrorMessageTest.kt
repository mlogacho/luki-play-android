// test/data/auth/AuthErrorMessageTest.kt
package com.luki.play.data.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * El portal muestra el `message` que manda el backend, con "Sin conexión…"
 * ante fallo de red y un genérico por pantalla en el resto. Este helper unifica
 * esa regla para Login y Recuperar contraseña, que antes divergían (solo el
 * login parseaba el mensaje del backend).
 */
class AuthErrorMessageTest {

    private fun httpError(code: Int, body: String): HttpException =
        HttpException(Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `usa el message del backend cuando viene`() {
        val error = httpError(400, """{"message":"La cédula no está registrada"}""")

        assertEquals(
            "La cédula no está registrada",
            AuthErrorMessage.of(error, fallback = "genérico"),
        )
    }

    @Test
    fun `cae al fallback si el cuerpo no trae message`() {
        val error = httpError(500, """{"error":"boom"}""")

        assertEquals("genérico", AuthErrorMessage.of(error, fallback = "genérico"))
    }

    @Test
    fun `cae al fallback si el message viene vacio`() {
        val error = httpError(400, """{"message":""}""")

        assertEquals("genérico", AuthErrorMessage.of(error, fallback = "genérico"))
    }

    @Test
    fun `un fallo de red da el mensaje sin conexion, no el fallback`() {
        val message = AuthErrorMessage.of(IOException("timeout"), fallback = "genérico")

        assertEquals("Sin conexión. Verifica tu internet e intenta de nuevo.", message)
    }

    @Test
    fun `cuerpo ilegible cae al fallback sin romper`() {
        val error = httpError(400, "esto no es json")

        assertEquals("genérico", AuthErrorMessage.of(error, fallback = "genérico"))
    }
}
