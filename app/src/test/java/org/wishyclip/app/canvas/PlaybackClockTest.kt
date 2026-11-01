package org.wishyclip.app.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {

    /** Simulates playback for [seconds]; each tick takes [workNs]; sleeps are whole milliseconds. */
    private fun ticksIn(fps: Int, seconds: Int, workNs: (Int) -> Long): Int {
        var t = 0L
        val clock = PlaybackClock(fps, t)
        val end = seconds * 1_000_000_000L
        var ticks = 0
        while (t < end) {
            ticks++
            t += workNs(ticks)
            t += clock.delayAfterTick(t) * 1_000_000L
        }
        return ticks
    }

    @Test fun holdsTheRequestedRateWithoutDrift() {
        for (fps in listOf(6, 12, 24, 25, 30, 48, 60)) {
            val ticks = ticksIn(fps, 20) { 2_000_000L } // 2 ms of work per tick
            val expected = fps * 20
            assertTrue("fps=$fps ticks=$ticks expected~$expected", Math.abs(ticks - expected) <= expected * 0.01)
        }
    }

    @Test fun oldWholeMillisecondTimerPlayedFast() {
        // Documents the bug the clock replaces: 1000 / fps in whole ms.
        assertEquals(41L, 1000L / 24)            // 24.39 fps instead of 24
        assertEquals(16L, 1000L / 60)            // 62.5 fps instead of 60
        assertTrue(1000.0 / (1000L / 60) > 62.0)
    }

    @Test fun slowTickDoesNotCauseABurstOfFastFrames() {
        var t = 0L
        val clock = PlaybackClock(30, t)
        t += 500_000_000L // one tick took half a second
        val wait = clock.delayAfterTick(t)
        assertTrue("resynchronised, wait=$wait", wait in 1L..34L)
        // The following ticks are paced normally again, not back to back.
        var gaps = 0
        repeat(5) {
            val before = t
            t += clock.delayAfterTick(t) * 1_000_000L
            if (t - before >= 30_000_000L) gaps++
        }
        assertEquals(5, gaps)
    }

    @Test fun zeroFpsIsClampedNotDividedByZero() {
        val wait = PlaybackClock(0, 0L).delayAfterTick(0L)
        assertTrue(wait >= 1L)
    }
}
