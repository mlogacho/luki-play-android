// data/auth/api/TvAuthDto.kt
package com.luki.play.data.auth.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs del emparejamiento de TV (device-code). Contrato verificado contra
 * `frontend/services/tvAuthApi.ts`. La TV crea una sesión y sondea; cuando el
 * teléfono la activa, el poll devuelve tokens.
 */
@JsonClass(generateAdapter = true)
data class TvSessionRequest(
    @Json(name = "tvDeviceId") val tvDeviceId: String,
)

@JsonClass(generateAdapter = true)
data class TvSessionDto(
    @Json(name = "sessionId")         val sessionId: String? = null,
    @Json(name = "code")              val code: String? = null,
    @Json(name = "activationUrl")     val activationUrl: String? = null,
    @Json(name = "expiresInSeconds")  val expiresInSeconds: Int = 300,
)

@JsonClass(generateAdapter = true)
data class TvPollDto(
    // pending | authenticated | expired
    @Json(name = "status")       val status: String? = null,
    @Json(name = "accessToken")  val accessToken: String? = null,
    @Json(name = "refreshToken") val refreshToken: String? = null,
    @Json(name = "user")         val user: AuthUserDto? = null,
)
