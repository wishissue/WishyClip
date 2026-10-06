package org.wishyclip.app.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zoom / pan / rotation of the canvas view. Hoisted out of [DrawingCanvas] so menus can rotate the
 * canvas and so the transform survives a device rotation (it is saveable).
 */
@Stable
class CanvasViewState(zoom: Float = 1f, pan: Offset = Offset.Zero, rotation: Float = 0f) {
    var zoom by mutableFloatStateOf(zoom)
    var pan by mutableStateOf(pan)
    var rotation by mutableFloatStateOf(rotation)

    val isTransformed: Boolean get() = zoom != 1f || pan != Offset.Zero || rotation != 0f

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
        rotation = 0f
    }

    /** Rotates the whole view about its centre by [degrees] (positive = clockwise). */
    fun rotateBy(degrees: Float) {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        pan = Offset(pan.x * c - pan.y * s, pan.x * s + pan.y * c)
        rotation = normalize(rotation + degrees)
    }

    /** Keeps the same part of the canvas in view when the view is resized (device rotation). */
    fun rescalePan(factor: Float) {
        if (factor.isFinite() && factor > 0f) pan = Offset(pan.x * factor, pan.y * factor)
    }

    companion object {
        /** Maps an angle to (-180, 180]. */
        fun normalize(deg: Float): Float {
            var r = deg % 360f
            if (r > 180f) r -= 360f
            if (r <= -180f) r += 360f
            return r
        }

        val Saver: Saver<CanvasViewState, Any> = listSaver(
            save = { listOf(it.zoom, it.pan.x, it.pan.y, it.rotation) },
            restore = { CanvasViewState(it[0], Offset(it[1], it[2]), it[3]) }
        )
    }
}
