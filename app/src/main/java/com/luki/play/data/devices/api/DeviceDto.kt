// data/devices/api/DeviceDto.kt
package com.luki.play.data.devices.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs de dispositivos. Contrato verificado contra `deviceApi.ts` del portal
 * (`DeviceListItem` / `DevicesResponse`). Campos con default para tolerar
 * respuestas parciales; Moshi ignora claves extra (p. ej. `registeredAt`).
 */
@JsonClass(generateAdapter = true)
data class DeviceItemDto(
    @Json(name = "id")                val id: String? = null,
    @Json(name = "deviceFingerprint") val deviceFingerprint: String? = null,
    @Json(name = "nombre")            val nombre: String? = null,
    @Json(name = "tipo")              val tipo: String? = null,
    @Json(name = "os")                val os: String? = null,
    @Json(name = "browser")           val browser: String? = null,
    @Json(name = "ipAddress")         val ipAddress: String? = null,
    @Json(name = "lastSeenAt")        val lastSeenAt: String? = null,
    @Json(name = "isCurrentDevice")   val isCurrentDevice: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class DevicesResponseDto(
    @Json(name = "devices") val devices: List<DeviceItemDto> = emptyList(),
    @Json(name = "count")   val count: Int = 0,
    // El backend siempre devuelve el tope del plan; 3 es el respaldo del portal.
    @Json(name = "limit")   val limit: Int = 3,
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    @Json(name = "deviceFingerprint") val deviceFingerprint: String,
    @Json(name = "tipo")              val tipo: String,
    @Json(name = "os")                val os: String,
    @Json(name = "browser")           val browser: String?,
    @Json(name = "nombre")            val nombre: String? = null,
)

@JsonClass(generateAdapter = true)
data class RenameDeviceRequest(
    @Json(name = "nombre") val nombre: String,
)
