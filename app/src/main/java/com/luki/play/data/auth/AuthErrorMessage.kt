// data/auth/AuthErrorMessage.kt
package com.luki.play.data.auth

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import java.io.IOException

/**
 * Traduce un fallo de auth a un mensaje para el usuario, con la misma regla
 * que el portal (`login.tsx`): si el backend mandó `{ message }`, ese texto
 * gana; si no hubo red, "Sin conexión…"; en cualquier otro caso, el
 * [fallback] propio de la pantalla.
 *
 * Vive fuera de los ViewModels para que Login y RecuperarContraseña hablen
 * exactamente igual — antes solo el login parseaba el mensaje del backend y
 * la recuperación mostraba siempre un genérico.
 */
/**
 * Cuerpo de error del backend. Top-level y con codegen (`generateAdapter =
 * true`) para que Moshi genere su adapter en compilación; una data class
 * anidada con `generateAdapter = false` obligaba a reflexión y reventaba el
 * `object` al inicializarse.
 */
@JsonClass(generateAdapter = true)
internal data class ApiErrorDto(val message: String?)

object AuthErrorMessage {

    private val adapter = Moshi.Builder().build().adapter(ApiErrorDto::class.java)

    private const val OFFLINE = "Sin conexión. Verifica tu internet e intenta de nuevo."

    fun of(t: Throwable, fallback: String): String = when (t) {
        is HttpException -> runCatching {
            t.response()?.errorBody()?.string()?.let { adapter.fromJson(it)?.message }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
        is IOException -> OFFLINE
        else -> fallback
    }
}
