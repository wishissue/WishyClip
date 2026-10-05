package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import org.wishyclip.app.model.Tool
import kotlin.math.max
import kotlin.math.min

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float
)

/**
 * Renders strokes onto layer bitmaps with dirty-rect optimization for 1080p performance,
 * plus pressure and tilt sensitivity support.
 */
class StrokeRenderer {
    private val path = Path()
    private val points = ArrayList<StrokePoint>()
    private var lastX = 0f
    private var lastY = 0f
    private var before: Bitmap? = null
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE

    val active: Boolean get() = before != null

    fun begin(snapshot: Bitmap, x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        before = snapshot
        path.reset()
        points.clear()
        path.moveTo(x, y)
        lastX = x
        lastY = y
        points.add(StrokePoint(x, y, pressure, tilt))
        minX = x; maxX = x
        minY = y; maxY = y
    }

    fun moveTo(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        lastX = x
        lastY = y
        points.add(StrokePoint(x, y, pressure, tilt))
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    fun render(target: Bitmap, tool: Tool, argb: Int, size: Float, opacity: Float) {
        val snapshot = before ?: return
        if (points.isEmpty()) return

        // Compute dirty rect around stroke with margin for stroke size and antialiasing
        val margin = size * 2.5f + 16f
        val left = (minX - margin).toInt().coerceIn(0, target.width)
        val top = (minY - margin).toInt().coerceIn(0, target.height)
        val right = (maxX + margin).toInt().coerceIn(0, target.width)
        val bottom = (maxY + margin).toInt().coerceIn(0, target.height)

        if (right <= left || bottom <= top) return

        val dirtyRect = Rect(left, top, right, bottom)

        // Restore target pixels inside dirtyRect from snapshot
        BitmapOps.replaceRect(target, snapshot, dirtyRect)

        val canvas = Canvas(target)
        canvas.save()
        canvas.clipRect(dirtyRect)

        var hasVariablePressure = false
        for (i in points.indices) {
            val p = points[i].pressure
            if (p < 0.95f || p > 1.05f) {
                hasVariablePressure = true
                break
            }
        }

        if (hasVariablePressure && points.size > 1 && tool != Tool.MARKER && tool != Tool.LINE && tool != Tool.RECT && tool != Tool.ELLIPSE) {
            val basePaint = BrushPaints.create(tool, argb, size, opacity)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val avgPressure = (p1.pressure + p2.pressure) / 2f
                val scale = 0.2f + 0.8f * avgPressure
                basePaint.strokeWidth = max(1f, size * scale)
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, basePaint)
            }
        } else {
            val paint = BrushPaints.create(tool, argb, size, opacity)
            when (tool) {
                Tool.LINE -> {
                    val p1 = points[0]
                    canvas.drawLine(p1.x, p1.y, lastX, lastY, paint)
                }
                Tool.RECT -> {
                    val p1 = points[0]
                    val l = min(p1.x, lastX)
                    val t = min(p1.y, lastY)
                    val r = max(p1.x, lastX)
                    val b = max(p1.y, lastY)
                    canvas.drawRect(l, t, r, b, paint)
                }
                Tool.ELLIPSE -> {
                    val p1 = points[0]
                    val l = min(p1.x, lastX)
                    val t = min(p1.y, lastY)
                    val r = max(p1.x, lastX)
                    val b = max(p1.y, lastY)
                    canvas.drawOval(l, t, r, b, paint)
                }
                else -> {
                    val drawn = Path(path)
                    drawn.lineTo(lastX + 0.01f, lastY)
                    canvas.drawPath(drawn, paint)
                }
            }
        }

        canvas.restore()
    }

    /** Ends the stroke and returns the pre-stroke snapshot (ownership passes to the caller). */
    fun finish(): Bitmap? {
        val snapshot = before
        before = null
        path.reset()
        points.clear()
        return snapshot
    }
}
