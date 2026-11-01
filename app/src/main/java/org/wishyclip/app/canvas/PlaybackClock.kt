package org.wishyclip.app.canvas

/**
 * Frame pacing for playback, as pure arithmetic (no Android) so it can be unit-tested.
 *
 * Ticks are scheduled on an absolute timeline (start + n * tick) instead of "wait one frame length
 * after each tick", so work time and rounding do not accumulate into drift. If a tick runs so long
 * that we are more than a whole frame behind, the schedule is reset to "now" (we drop time instead
 * of fast-forwarding through a burst of frames).
 */
class PlaybackClock(fps: Int, startNs: Long) {
    private val tickNs: Long = 1_000_000_000L / fps.coerceAtLeast(1)
    private var next: Long = startNs

    /** Call when a tick has finished at [nowNs]; returns how many ms to wait before the next tick. */
    fun delayAfterTick(nowNs: Long): Long {
        next += tickNs
        if (nowNs - next > tickNs) next = nowNs
        return ((next - nowNs) / 1_000_000L).coerceAtLeast(1L)
    }
}
