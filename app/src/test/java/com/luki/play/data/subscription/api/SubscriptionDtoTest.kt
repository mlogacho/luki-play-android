// test/data/subscription/api/SubscriptionDtoTest.kt
package com.luki.play.data.subscription.api

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ancla `GET /public/me/plan` al shape REAL del backend (`MePlanResponse` en
 * `frontend/services/api/subscriptionApi.ts`). Si el backend renombra un campo,
 * la pantalla de suscripción mostraría datos vacíos en silencio.
 */
class SubscriptionDtoTest {

    private val adapter = Moshi.Builder().build().adapter(MePlanDto::class.java)

    @Test
    fun `parsea plan mas suscripcion completos`() {
        // Copia literal del shape de MePlanResponse (subscriptionApi.ts:5-31),
        // con campos extra (duracionDias, entitlements…) que Moshi debe ignorar.
        val json = """
            {
              "plan": {
                "id": "plan_1",
                "nombre": "LukiPlay",
                "descripcion": "Todo el contenido",
                "precio": 9.99,
                "moneda": "USD",
                "duracionDias": 30,
                "gracePeriodDays": 5,
                "maxDevices": 3,
                "maxConcurrentStreams": 2,
                "maxProfiles": 5,
                "videoQuality": "FHD",
                "allowDownloads": true,
                "allowCasting": true,
                "hasAds": false,
                "trialDays": 0,
                "entitlements": ["lukiplay"]
              },
              "subscription": {
                "id": "sub_1",
                "status": "ACTIVE",
                "startDate": "2026-07-01T00:00:00.000Z",
                "expirationDate": "2026-08-01T00:00:00.000Z",
                "gracePeriodEnd": null
              }
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("LukiPlay", dto.plan?.nombre)
        assertEquals(9.99, dto.plan?.precio!!, 0.001)
        assertEquals("USD", dto.plan?.moneda)
        assertEquals("FHD", dto.plan?.videoQuality)
        assertEquals(3, dto.plan?.maxDevices)
        assertEquals(2, dto.plan?.maxConcurrentStreams)
        assertEquals(5, dto.plan?.maxProfiles)
        assertEquals("ACTIVE", dto.subscription?.status)
        assertEquals("2026-08-01T00:00:00.000Z", dto.subscription?.expirationDate)
        assertNull(dto.subscription?.gracePeriodEnd)
    }

    @Test
    fun `tolera subscription nula y precio nulo`() {
        // El backend permite subscription = null y precio = null.
        val json = """
            {
              "plan": {
                "nombre": "Basic",
                "descripcion": "",
                "precio": null,
                "moneda": "USD",
                "maxDevices": 1,
                "maxConcurrentStreams": 1,
                "maxProfiles": 1,
                "videoQuality": "HD"
              },
              "subscription": null
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals("Basic", dto.plan?.nombre)
        assertNull(dto.plan?.precio)
        assertNull(dto.subscription)
    }
}
