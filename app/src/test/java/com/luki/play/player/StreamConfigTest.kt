// test/player/StreamConfigTest.kt
package com.luki.play.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamConfigTest {

    @Test
    fun `effectiveManifestType respects explicit HLS`() {
        val cfg = StreamConfig(url = "https://x/y.mpd", manifestType = ManifestType.HLS)
        assertEquals(ManifestType.HLS, cfg.effectiveManifestType())
    }

    @Test
    fun `effectiveManifestType infers HLS from m3u8`() {
        val cfg = StreamConfig(url = "https://x/canal.m3u8")
        assertEquals(ManifestType.HLS, cfg.effectiveManifestType())
    }

    @Test
    fun `effectiveManifestType infers DASH from mpd`() {
        val cfg = StreamConfig(url = "https://x/canal.mpd?token=1")
        assertEquals(ManifestType.DASH, cfg.effectiveManifestType())
    }

    @Test
    fun `effectiveManifestType OTHER for unknown extension`() {
        val cfg = StreamConfig(url = "https://x/stream")
        assertEquals(ManifestType.OTHER, cfg.effectiveManifestType())
    }

    @Test
    fun `hasDrm requires both scheme and license url`() {
        assertFalse(StreamConfig(url = "u", drmScheme = DrmScheme.NONE).hasDrm())
        assertFalse(StreamConfig(url = "u", drmScheme = DrmScheme.WIDEVINE).hasDrm())
        assertTrue(
            StreamConfig(
                url = "u",
                drmScheme = DrmScheme.WIDEVINE,
                licenseUrl = "https://license/wv",
            ).hasDrm()
        )
    }
}
