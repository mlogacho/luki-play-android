// data/auth/api/AuthDto.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs de la API de autenticación.
 *
 * Moshi codegen genera adaptadores en tiempo de compilación (sin reflexión),
 * lo que elimina la necesidad de reglas ProGuard extra.
 *
 * Contrato verificado contra el backend real (Luki-Play-OTT,
 * `contract-login.use-case.ts` / `id-number-login`, 2026-07-21): los logins
 * devuelven `{ accessToken, refreshToken, user: { id, name, email, plan } }`
 * — el usuario viene ANIDADO en `user`, no plano. `/auth/refresh` devuelve
 * `{ accessToken, refreshToken, canAccessOtt, restrictionMessage }` sin
 * `user`, por eso todos los campos de identidad son opcionales.
 */

@JsonClass(generateAdapter = true)
data class IdLoginRequest(
    @Json(name = "idNumber")  val idNumber: String,
    @Json(name = "password")  val password: String,
    @Json(name = "deviceId")  val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class ContractLoginRequest(
    @Json(name = "contractNumber") val contractNumber: String,
    @Json(name = "password")       val password: String,
    @Json(name = "deviceId")       val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refreshToken") val refreshToken: String,
)

/** Usuario anidado en la respuesta de login — shape real del backend. */
@JsonClass(generateAdapter = true)
data class AuthUserDto(
    @Json(name = "id")    val id: String?,
    @Json(name = "name")  val name: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "plan")  val plan: String?,
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "accessToken")  val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String?,
    /** Presente en login; ausente en refresh. */
    @Json(name = "user")         val user: AuthUserDto? = null,
    // Alias planos legacy — tolerancia por si algún endpoint viejo los usa.
    @Json(name = "userId")       val userId: String? = null,
    @Json(name = "id")           val idAlt: String? = null,
    @Json(name = "nombre")       val nombre: String? = null,
    @Json(name = "name")         val nameAlt: String? = null,
) {
    fun resolvedUserId(): String = user?.id ?: userId ?: idAlt ?: ""
    fun resolvedDisplayName(): String = user?.name ?: nombre ?: nameAlt ?: ""
}
