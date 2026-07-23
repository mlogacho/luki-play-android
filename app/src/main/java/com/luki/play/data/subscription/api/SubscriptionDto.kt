// data/subscription/api/SubscriptionDto.kt
package com.luki.play.data.subscription.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Respuesta de `GET /public/me/plan`.
 *
 * Contrato verificado contra `MePlanResponse` del portal
 * (`frontend/services/api/subscriptionApi.ts`). Solo se declaran los campos que
 * la pantalla usa; Moshi ignora las claves extra. Los numéricos y strings
 * llevan default para que una respuesta parcial no reviente el parseo.
 */
@JsonClass(generateAdapter = true)
data class MePlanDto(
    @Json(name = "plan")         val plan: PlanDto? = null,
    @Json(name = "subscription") val subscription: SubscriptionDto? = null,
)

@JsonClass(generateAdapter = true)
data class PlanDto(
    @Json(name = "nombre")               val nombre: String? = null,
    @Json(name = "descripcion")          val descripcion: String? = null,
    // `precio` puede ser null en el backend (plan sin precio listado).
    @Json(name = "precio")               val precio: Double? = null,
    @Json(name = "moneda")               val moneda: String? = null,
    @Json(name = "videoQuality")         val videoQuality: String? = null,
    @Json(name = "maxDevices")           val maxDevices: Int = 0,
    @Json(name = "maxConcurrentStreams") val maxConcurrentStreams: Int = 0,
    @Json(name = "maxProfiles")          val maxProfiles: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SubscriptionDto(
    // ACTIVE | GRACE_PERIOD | SUSPENDED | CANCELLED
    @Json(name = "status")         val status: String? = null,
    @Json(name = "startDate")      val startDate: String? = null,
    @Json(name = "expirationDate") val expirationDate: String? = null,
    @Json(name = "gracePeriodEnd") val gracePeriodEnd: String? = null,
)
