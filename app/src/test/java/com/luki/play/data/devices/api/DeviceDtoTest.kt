// test/data/devices/api/DeviceDtoTest.kt
package com.luki.play.data.devices.api

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ancla `GET /public/devices` al shape del portal (`DevicesResponse` /
 * `DeviceListItem` en `deviceApi.ts`).
 */
class DeviceDtoTest {

    private val adapter = Moshi.Builder().build().adapter(DevicesResponseDto::class.java)

    @Test
    fun `parsea la lista de dispositivos con su tope`() {
        val json = """
            {
              "devices": [
                {
                  "id": "dev_1",
                  "deviceFingerprint": "fp-aaa",
                  "nombre": "Mi teléfono",
                  "tipo": "MOBILE",
                  "os": "Android 14",
                  "browser": "Pixel 8",
                  "modelo": "Pixel 8",
                  "ipAddress": "190.1.2.3",
                  "lastSeenAt": "2026-07-23T10:00:00.000Z",
                  "registeredAt": "2026-07-01T10:00:00.000Z",
                  "isCurrentDevice": true
                },
                {
                  "id": "dev_2",
                  "deviceFingerprint": "fp-bbb",
                  "nombre": null,
                  "tipo": "SMART_TV",
                  "os": null,
                  "browser": null,
                  "ipAddress": null,
                  "lastSeenAt": null,
                  "registeredAt": "2026-07-02T10:00:00.000Z",
                  "isCurrentDevice": false
                }
              ],
              "count": 2,
              "limit": 3
            }
        """.trimIndent()

        val dto = adapter.fromJson(json)!!

        assertEquals(2, dto.devices.size)
        assertEquals(3, dto.limit)
        val first = dto.devices[0]
        assertEquals("fp-aaa", first.deviceFingerprint)
        assertEquals("Mi teléfono", first.nombre)
        assertEquals("MOBILE", first.tipo)
        assertTrue(first.isCurrentDevice)
        val second = dto.devices[1]
        assertNull(second.nombre)
        assertNull(second.os)
        assertFalse(second.isCurrentDevice)
    }

    @Test
    fun `limit ausente cae al respaldo de 3 y lista vacia`() {
        val dto = adapter.fromJson("""{"devices":[],"count":0}""")!!

        assertTrue(dto.devices.isEmpty())
        assertEquals(3, dto.limit)
    }
}
