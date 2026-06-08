// test/player/qos/QoSAnalyticsListenerTest.kt
package com.luki.play.player.qos

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del reductor de eventos. No reproduce nada — sólo simula la secuencia
 * de callbacks que ExoPlayer emite y verifica el snapshot resultante.
 */
@UnstableApi
class QoSAnalyticsListenerTest {

    private val eventTime: AnalyticsListener.EventTime = mockk(relaxed = true)

    @Test
    fun `startup time is positive after first READY`() {
        val q = QoSAnalyticsListener()
        q.onLoadStarted("https://x/y.m3u8")

        q.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        Thread.sleep(20)
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val s = q.snapshot()
        assertTrue("startup should be > 0, was ${s.startupTimeMs}", s.startupTimeMs >= 0)
        assertEquals("https://x/y.m3u8", s.streamUrl)
        assertEquals(0, s.rebufferCount)
        assertNull(s.fatalErrorCode)
    }

    @Test
    fun `rebuffer cycle increments count and accumulates ms`() {
        val q = QoSAnalyticsListener()
        q.onLoadStarted("u")
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)   // startup

        // primer rebuffer
        q.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        Thread.sleep(15)
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        // segundo rebuffer
        q.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        Thread.sleep(10)
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val s = q.snapshot()
        assertEquals(2, s.rebufferCount)
        assertTrue("rebufferMs should accumulate, was ${s.rebufferMs}", s.rebufferMs >= 20)
    }

    @Test
    fun `buffering before first ready is not counted as rebuffer`() {
        val q = QoSAnalyticsListener()
        q.onLoadStarted("u")
        q.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        assertEquals(0, q.snapshot().rebufferCount)
    }

    @Test
    fun `onLoadStarted resets all metrics`() {
        val q = QoSAnalyticsListener()
        q.onLoadStarted("u1")
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)
        q.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        q.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        q.onLoadStarted("u2")
        val s = q.snapshot()
        assertEquals("u2", s.streamUrl)
        assertEquals(0, s.rebufferCount)
        assertEquals(0L, s.rebufferMs)
        assertEquals(-1L, s.startupTimeMs)
    }
}
