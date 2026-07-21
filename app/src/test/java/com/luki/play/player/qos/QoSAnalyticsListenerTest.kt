// test/player/qos/QoSAnalyticsListenerTest.kt
package com.luki.play.player.qos

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test del reductor de eventos. No reproduce nada — sólo simula la secuencia
 * de callbacks que ExoPlayer emite y verifica el snapshot resultante.
 *
 * Usa un reloj falso inyectado en vez de SystemClock (API de Android que en
 * unit tests JVM lanza "Method not mocked") y de Thread.sleep (flaky). Cada
 * test avanza `nowMs` a mano y asserta valores exactos.
 */
@UnstableApi
class QoSAnalyticsListenerTest {

    private val eventTime: AnalyticsListener.EventTime = mockk(relaxed = true)

    private var nowMs = 1_000L
    private val listener = QoSAnalyticsListener { nowMs }

    @Test
    fun `startup time is measured between load and first READY`() {
        listener.onLoadStarted("https://x/y.m3u8")

        listener.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        nowMs += 250
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val s = listener.snapshot()
        assertEquals(250L, s.startupTimeMs)
        assertEquals("https://x/y.m3u8", s.streamUrl)
        assertEquals(0, s.rebufferCount)
        assertNull(s.fatalErrorCode)
    }

    @Test
    fun `rebuffer cycle increments count and accumulates ms`() {
        listener.onLoadStarted("u")
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)   // startup

        // primer rebuffer: 15 ms
        listener.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        nowMs += 15
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        // segundo rebuffer: 10 ms
        listener.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        nowMs += 10
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val s = listener.snapshot()
        assertEquals(2, s.rebufferCount)
        assertEquals(25L, s.rebufferMs)
    }

    @Test
    fun `buffering before first ready is not counted as rebuffer`() {
        listener.onLoadStarted("u")
        listener.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        nowMs += 100
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val s = listener.snapshot()
        assertEquals(0, s.rebufferCount)
        assertEquals(0L, s.rebufferMs)
        assertEquals(100L, s.startupTimeMs)
    }

    @Test
    fun `onLoadStarted resets all metrics`() {
        listener.onLoadStarted("u1")
        nowMs += 50
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)
        listener.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        nowMs += 20
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)
        listener.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        listener.onErrorRecovered()

        listener.onLoadStarted("u2")
        val s = listener.snapshot()
        assertEquals("u2", s.streamUrl)
        assertEquals(0, s.rebufferCount)
        assertEquals(0L, s.rebufferMs)
        assertEquals(-1L, s.startupTimeMs)
        assertEquals(0, s.recoveredErrorCount)
        assertNull(s.fatalErrorCode)
    }

    @Test
    fun `fatal error records its code`() {
        listener.onLoadStarted("u")
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        listener.onFatalError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            listener.snapshot().fatalErrorCode,
        )
    }

    @Test
    fun `recovered errors do not mark the session fatal`() {
        listener.onLoadStarted("u")
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        // El manager recuperó (p.ej. reenganche BLW): cuenta aparte, no fatal.
        listener.onErrorRecovered()

        var s = listener.snapshot()
        assertNull(s.fatalErrorCode)
        assertEquals(1, s.recoveredErrorCount)

        // Cap de reenganches agotado: el manager lo declara fatal explícitamente.
        listener.onFatalError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
        s = listener.snapshot()
        assertEquals(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, s.fatalErrorCode)
        assertEquals(1, s.recoveredErrorCount)
    }

    @Test
    fun `IDLE and ENDED transitions do not disturb metrics`() {
        listener.onLoadStarted("u")
        nowMs += 100
        listener.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        listener.onPlaybackStateChanged(eventTime, Player.STATE_IDLE)
        listener.onPlaybackStateChanged(eventTime, Player.STATE_ENDED)

        val s = listener.snapshot()
        assertEquals(100L, s.startupTimeMs)
        assertEquals(0, s.rebufferCount)
        assertEquals(0L, s.rebufferMs)
        assertNull(s.fatalErrorCode)
    }
}
