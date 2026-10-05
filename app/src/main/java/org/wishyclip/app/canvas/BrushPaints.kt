package org.wishyclip.app.canvas

import android.graphics.BlurMaskFilter
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.wishyclip.app.model.Tool

object BrushPaints {
    /** [argb] is an Android color int, [size] is in bitmap pixels, [opacity] 0..1. */
    fun create(tool: Tool, argb: Int, size: Float, opacity: Float): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        val a = opacity.coerceIn(0f, 1f)
        when (tool) {
            Tool.PEN -> {
                paint.color = argb
                paint.strokeWidth = size
                paint.alpha = (a * 255f).toInt()
            }
            Tool.PENCIL -> {
                paint.color = argb
                paint.strokeWidth = (size * 0.6f).coerceAtLeast(1f)
                paint.alpha = (a * 0.85f * 255f).toInt()
                paint.pathEffect = DiscretePathEffect(3f, 0.8f)
            }
            Tool.MARKER -> {
                paint.color = argb
                paint.strokeWidth = size * 1.8f
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (a * 0.65f * 255f).toInt()
            }
            Tool.ERASER -> {
                paint.color = 0xFF000000.toInt()
                paint.strokeWidth = size * 1.5f
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            Tool.AIRBRUSH -> {
                paint.color = argb
                paint.strokeWidth = size * 2f
                paint.alpha = (a * 0.3f * 255f).toInt()
                paint.maskFilter = BlurMaskFilter(size.coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
            }
            Tool.CALLIGRAPHY -> {
                paint.color = argb
                paint.strokeWidth = size * 1.2f
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (a * 255f).toInt()
            }
            Tool.HIGHLIGHTER -> {
                paint.color = argb
                paint.strokeWidth = size * 2.5f
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (a * 0.4f * 255f).toInt()
            }
            Tool.FILL, Tool.LASSO, Tool.LINE, Tool.RECT, Tool.ELLIPSE, Tool.TEXT -> {
                paint.color = argb
                paint.strokeWidth = size
                paint.alpha = (a * 255f).toInt()
            }
        }
        return paint
    }
}
