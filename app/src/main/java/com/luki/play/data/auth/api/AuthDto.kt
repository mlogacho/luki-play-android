// data/auth/api/AuthDto.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs de la API de autenticación.
 *
 * Moshi codegen genera adaptadores en tiempo de compilación (sin reflexión),
 * lo que elimina la necesidad de reglas ProGuard extra.
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

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "accessToken")  val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String?,
    @Json(name = "userId")       val userId: String?,
    @Json(name = "id")           val idAlt: String?,
    @Json(name = "nombre")       val nombre: String?,
    @Json(name = "name")         val nameAlt: String?,
) {
    fun resolvedUserId(): String = userId ?: idAlt ?: ""
    fun resolvedDisplayName(): String = nombre ?: nameAlt ?: ""
}
