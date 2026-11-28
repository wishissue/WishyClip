package org.wishyclip.app.model

/**
 * Hard limits for new projects. Pure Kotlin (no Android types) so it is covered by plain JVM tests.
 *
 * A painted layer costs width * height * 4 bytes, and several frames are cached at once, so an unbounded
 * canvas is an out-of-memory crash waiting to happen. 4096 x 2160 (about 8.8 megapixels, 35 MB per layer)
 * is the ceiling; 3840 x 2160 (UHD) fits.
 */
object ProjectLimits {
    const val MIN_SIDE = 64
    const val MAX_SIDE = 4096
    const val MAX_PIXELS = 4096L * 2160L
    const val MIN_FPS = 1
    const val MAX_FPS = 60
    const val MAX_NAME_LENGTH = 80

    /** A canvas size that is always valid: each side within bounds and the total within [MAX_PIXELS]. */
    data class Size(val width: Int, val height: Int)

    /**
     * Clamps both sides into [MIN_SIDE]..[MAX_SIDE]; if the area is still too large, scales both down
     * together so the aspect ratio is kept.
     */
    fun fit(width: Int, height: Int): Size {
        var w = width.coerceIn(MIN_SIDE, MAX_SIDE)
        var h = height.coerceIn(MIN_SIDE, MAX_SIDE)
        val pixels = w.toLong() * h.toLong()
        if (pixels > MAX_PIXELS) {
            val k = Math.sqrt(MAX_PIXELS.toDouble() / pixels.toDouble())
            w = Math.floor(w * k).toInt().coerceIn(MIN_SIDE, MAX_SIDE)
            h = Math.floor(h * k).toInt().coerceIn(MIN_SIDE, MAX_SIDE)
            // Floor can still overshoot by rounding of the other side only when a side hit MIN_SIDE.
            while (w.toLong() * h.toLong() > MAX_PIXELS && h > MIN_SIDE) h--
            while (w.toLong() * h.toLong() > MAX_PIXELS && w > MIN_SIDE) w--
        }
        return Size(w, h)
    }

    fun cleanFps(fps: Int): Int = fps.coerceIn(MIN_FPS, MAX_FPS)

    fun cleanName(name: String): String =
        name.trim().take(MAX_NAME_LENGTH).ifEmpty { "Untitled" }
}
