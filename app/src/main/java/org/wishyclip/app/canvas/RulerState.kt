package org.wishyclip.app.canvas

import kotlin.math.hypot

/** Which part of the ruler a touch grabbed. */
enum class RulerHandle { NONE, A, B, CENTER }

/**
 * A straight edge in bitmap coordinates. A stroke that *starts* within snap distance of it is
 * locked to the edge for its whole length (points are projected onto the segment); a stroke that
 * starts elsewhere is free. The three handles (both ends and the centre) move and rotate it.
 */
data class RulerState(val ax: Float, val ay: Float, val bx: Float, val by: Float) {

    val length: Float get() = hypot(bx - ax, by - ay)

    fun moved(dx: Float, dy: Float) = RulerState(ax + dx, ay + dy, bx + dx, by + dy)

    /** Distance from (x, y) to the closest point of the segment. */
    fun distanceTo(x: Float, y: Float): Float {
        val out = FloatArray(2)
        project(x, y, out)
        return hypot(x - out[0], y - out[1])
    }

    /** Writes the closest point of the segment to (x, y) into [out] (x at 0, y at 1). */
    fun project(x: Float, y: Float, out: FloatArray) {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) {
            out[0] = ax; out[1] = ay
            return
        }
        val t = (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
        out[0] = ax + dx * t
        out[1] = ay + dy * t
    }

    /** Handle under (x, y), preferring the ends over the centre when the ruler is short. */
    fun hit(x: Float, y: Float, radius: Float): RulerHandle {
        if (hypot(x - ax, y - ay) <= radius) return RulerHandle.A
        if (hypot(x - bx, y - by) <= radius) return RulerHandle.B
        if (hypot(x - (ax + bx) / 2f, y - (ay + by) / 2f) <= radius) return RulerHandle.CENTER
        return RulerHandle.NONE
    }

    /** Applies a drag of [handle] by (dx, dy). */
    fun drag(handle: RulerHandle, dx: Float, dy: Float): RulerState = when (handle) {
        RulerHandle.A -> copy(ax = ax + dx, ay = ay + dy)
        RulerHandle.B -> copy(bx = bx + dx, by = by + dy)
        RulerHandle.CENTER -> moved(dx, dy)
        RulerHandle.NONE -> this
    }

    companion object {
        /** Horizontal ruler across the middle, 80% of the canvas width. */
        fun default(width: Int, height: Int) =
            RulerState(width * 0.1f, height * 0.5f, width * 0.9f, height * 0.5f)
    }
}
