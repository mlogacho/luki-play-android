// test/player/StreamConfigSessionOwnershipTest.kt
package com.luki.play.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protege el reparto de responsabilidad sobre la sesión de stream.
 *
 * Solo puede haber UN dueño por visionado. Cuando el reproductor lo lanza el
 * bridge, la sesión ya la abrió el portal dentro del WebView: si el nativo
 * abriera otra, un mismo visionado gastaría DOS cupos del plan y un solo
 * dispositivo podría disparar el límite.
 *
 * Por eso el valor por defecto es "no soy el dueño": quien quiera gestionarla
 * tiene que pedirlo explícitamente.
 */
class StreamConfigSessionOwnershipTest {

    @Test
    fun `por defecto el reproductor NO gestiona la sesion`() {
        val fromBridge = StreamConfig(url = "https://cdn/x.m3u8")

        assertFalse(
            "el bridge no debe abrir sesion: ya la abrio el portal",
            fromBridge.managesStreamSession(),
        )
    }

    @Test
    fun `el camino nativo la gestiona cuando lo pide y hay canal`() {
        val native = StreamConfig(
            url = "https://cdn/x.m3u8",
            channelId = "canal-1",
            ownsStreamSession = true,
        )

        assertTrue(native.managesStreamSession())
    }

    @Test
    fun `pedirlo sin channelId no basta`() {
        // Sin id no hay nada que abrir; activarlo dejaria una sesion a medias.
        val incomplete = StreamConfig(
            url = "https://cdn/x.m3u8",
            ownsStreamSession = true,
        )

        assertFalse(incomplete.managesStreamSession())
    }
}
