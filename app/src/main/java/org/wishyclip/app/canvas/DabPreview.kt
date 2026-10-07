package org.wishyclip.app.canvas

import android.graphics.Canvas
import android.graphics.Paint
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a wavy sample stroke of a [DabBrush] using the same stamping rules as [StrokeRenderer]
 * (spacing, rotation, scatter, size jitter, flow), so the preview shows what the canvas will draw.
 */
object DabPreview {

    private const val MAX_STAMPS = 500

    fun draw(canvas: Canvas, brush: DabBrush, argb: Int, width: Float, height: Float, diameter: Float) {
        if (width <= 8f || height <= 8f) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.color = argb
        paint.alpha = (brush.flow * 255f).roundToInt().coerceIn(1, 255)
        val rnd = Random(7)

        val d = diameter.coerceIn(4f, height)
        val pad = d / 2f + 4f
        val usable = width - pad * 2f
        if (usable <= 1f) return
        val amp = ((height - d) / 2f).coerceAtLeast(0f) * 0.7f
        fun xAt(t: Float) = pad + usable * t
        fun yAt(t: Float) = height / 2f + sin(t * 2f * PI.toFloat()) * amp

        val scale = d / max(brush.tip.width, brush.tip.height).toFloat()
        var stamps = 0
        fun stamp(x: Float, y: Float, strokeAngle: Float) {
            if (stamps++ >= MAX_STAMPS) return
            val jitter = if (brush.sizeJitter > 0f) 1f - brush.sizeJitter * rnd.nextFloat() else 1f
            val scatterPx = brush.scatter * d
            val jx = if (scatterPx > 0f) (rnd.nextFloat() * 2f - 1f) * scatterPx else 0f
            val jy = if (scatterPx > 0f) (rnd.nextFloat() * 2f - 1f) * scatterPx else 0f
            val ang = brush.angle + if (brush.rotateWithStroke) strokeAngle else 0f
            val s = scale * jitter
            canvas.save()
            canvas.translate(x + jx * 0.5f, y + jy * 0.5f) // scatter is damped so it stays inside the swatch
            canvas.rotate(ang)
            canvas.scale(s, s)
            canvas.drawBitmap(brush.tip, -brush.tip.width / 2f, -brush.tip.height / 2f, paint)
            canvas.restore()
        }

        val step = max(1f, brush.spacing * d)
        val n = 160
        var prevX = xAt(0f)
        var prevY = yAt(0f)
        stamp(prevX, prevY, 0f)
        var carry = 0f
        for (i in 1..n) {
            val t = i / n.toFloat()
            val x = xAt(t)
            val y = yAt(t)
            val dx = x - prevX
            val dy = y - prevY
            val len = hypot(dx, dy)
            if (len < 0.001f) continue
            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            var traveled = step - carry
            while (traveled <= len && stamps < MAX_STAMPS) {
                val k = traveled / len
                stamp(prevX + dx * k, prevY + dy * k, angle)
                traveled += step
            }
            carry = len - (traveled - step)
            prevX = x
            prevY = y
        }
    }
}
