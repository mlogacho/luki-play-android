// test/data/auth/DeviceIdAdoptionTest.kt
package com.luki.play.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Semántica de [TokenStore.adoptDeviceId] — la pieza que evita que el mismo
 * aparato consuma DOS cupos de `deviceLimitPolicy` al migrar del login web
 * al nativo (la web ya registró su `localStorage['luki-device-id']`).
 *
 * Se testea contra una implementación en memoria equivalente a
 * [SecureTokenStore] (que necesita Context + EncryptedSharedPreferences).
 */
class DeviceIdAdoptionTest {

    @Test
    fun `adopta el id de la web cuando el nativo aun no tiene uno`() {
        val store = InMemoryDeviceIdStore()

        val effective = store.adoptDeviceId("web-uuid-123")

        assertEquals("web-uuid-123", effective)
        assertEquals("web-uuid-123", store.deviceId())
    }

    @Test
    fun `no sobrescribe un id nativo ya existente`() {
        val store = InMemoryDeviceIdStore()
        val propio = store.deviceId()   // fuerza la generación

        val effective = store.adoptDeviceId("web-uuid-123")

        assertEquals(propio, effective)
        assertEquals(propio, store.deviceId())
        assertNotEquals("web-uuid-123", store.deviceId())
    }

    @Test
    fun `es idempotente ante llamadas repetidas`() {
        val store = InMemoryDeviceIdStore()

        val first  = store.adoptDeviceId("web-uuid-123")
        val second = store.adoptDeviceId("web-uuid-456")
        val third  = store.adoptDeviceId("web-uuid-123")

        assertEquals("web-uuid-123", first)
        assertEquals("web-uuid-123", second)
        assertEquals("web-uuid-123", third)
    }

    @Test
    fun `un candidato vacio o en blanco no fija el id`() {
        val store = InMemoryDeviceIdStore()

        val effective = store.adoptDeviceId("   ")

        assertTrue("debe caer al generador", effective.isNotBlank())
        assertNotEquals("   ", effective)
        // Y el generado queda estable en llamadas sucesivas.
        assertEquals(effective, store.deviceId())
    }

    @Test
    fun `deviceId es estable entre llamadas`() {
        val store = InMemoryDeviceIdStore()
        assertEquals(store.deviceId(), store.deviceId())
    }

    @Test
    fun `consultar el id sin crearlo deja la adopcion posible`() {
        // El caso que rompia la unificacion: getDeviceInfo() del bridge corre
        // en el arranque (isTvDevice()) y, si consultara con deviceId(),
        // generaria un id propio ANTES de que la web entregara el suyo. Como
        // adoptDeviceId nunca sobrescribe, el aparato se quedaba con dos
        // identidades para siempre.
        val store = InMemoryDeviceIdStore()

        assertNull("consultar no debe crear", store.existingDeviceId())

        val effective = store.adoptDeviceId("web-uuid-123")

        assertEquals("web-uuid-123", effective)
        assertEquals("web-uuid-123", store.existingDeviceId())
    }
}

/** Réplica en memoria de la lógica de deviceId de [SecureTokenStore]. */
private class InMemoryDeviceIdStore {
    private var stored: String? = null

    fun existingDeviceId(): String? = stored?.takeIf { it.isNotBlank() }

    fun deviceId(): String {
        stored?.takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().also { stored = it }
    }

    fun adoptDeviceId(candidate: String): String {
        stored?.takeIf { it.isNotBlank() }?.let { return it }
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return deviceId()
        stored = trimmed
        return trimmed
    }
}
