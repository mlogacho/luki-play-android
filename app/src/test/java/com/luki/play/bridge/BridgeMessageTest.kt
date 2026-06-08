// test/bridge/BridgeMessageTest.kt
package com.luki.play.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el parseo JSON → [BridgeMessage] para los payloads que envía
 * la capa web a `window.LukiNative.dispatch(...)`.
 */
class BridgeMessageTest {

    @Test
    fun `play_stream con DRM Widevine se parsea con headers`() {
        val json = """
            {
              "type": "play_stream",
              "url":  "https://cdn/canal.mpd",
              "title": "HBO",
              "manifestType": "DASH",
              "drmScheme":    "WIDEVINE",
              "licenseUrl":   "https://license/wv",
              "licenseHeaders": {
                "X-AxDRM-Message": "tok",
                "Authorization":   "Bearer abc"
              },
              "drmMultiSession": true
            }
        """.trimIndent()

        val msg = BridgeMessage.from(json)
        assertTrue("expected PlayStream, got $msg", msg is BridgeMessage.PlayStream)
        msg as BridgeMessage.PlayStream

        assertEquals("https://cdn/canal.mpd", msg.url)
        assertEquals("HBO", msg.title)
        assertEquals("DASH", msg.manifestType)
        assertEquals("WIDEVINE", msg.drmScheme)
        assertEquals("https://license/wv", msg.licenseUrl)
        assertEquals(2, msg.licenseHeaders.size)
        assertEquals("tok", msg.licenseHeaders["X-AxDRM-Message"])
        assertEquals("Bearer abc", msg.licenseHeaders["Authorization"])
        assertTrue(msg.drmMultiSession)
    }

    @Test
    fun `play_stream sin DRM keeps defaults`() {
        val json = """
            { "type": "play_stream", "url": "https://x/y.m3u8" }
        """.trimIndent()

        val msg = BridgeMessage.from(json) as BridgeMessage.PlayStream
        assertNull(msg.drmScheme)
        assertNull(msg.licenseUrl)
        assertEquals(0, msg.licenseHeaders.size)
        assertEquals("", msg.title)
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(BridgeMessage.from("""{"type":"unknown"}"""))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(BridgeMessage.from("not-json"))
    }
}
