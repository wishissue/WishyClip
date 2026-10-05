package org.wishyclip.app.canvas

import android.graphics.BlurMaskFilter
import android.graphics.CornerPathEffect
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.wishyclip.app.model.Tool

object BrushPaints {
    /**
     * Final alpha (0..1) a stroke of [tool] is composited with. Strokes are drawn at full alpha
     * onto a scratch overlay and composited once with this factor, so overlapping segments
     * of one stroke never darken each other.
     */
    fun alphaFactor(tool: Tool, opacity: Float): Float {
        val a = opacity.coerceIn(0f, 1f)
        return when (tool) {
            Tool.PENCIL -> a * 0.85f
            Tool.MARKER -> a * 0.65f
            Tool.AIRBRUSH -> a * 0.3f
            Tool.HIGHLIGHTER -> a * 0.4f
            Tool.CHARCOAL -> a * 0.8f
            Tool.WATERCOLOR -> a * 0.35f
            Tool.CHALK -> a * 0.75f
            else -> a
        }
    }

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
            Tool.CHARCOAL -> {
                paint.color = argb
                paint.strokeWidth = size * 1.3f
                paint.alpha = (a * 0.8f * 255f).toInt()
                paint.pathEffect = DiscretePathEffect(2f, 2.5f)
            }
            Tool.INK -> {
                // Corner rounding gives the smooth, flowing line of a brush pen.
                paint.color = argb
                paint.strokeWidth = (size * 0.85f).coerceAtLeast(1f)
                paint.alpha = (a * 255f).toInt()
                paint.pathEffect = CornerPathEffect(size.coerceAtLeast(4f))
            }
            Tool.WATERCOLOR -> {
                paint.color = argb
                paint.strokeWidth = size * 2.2f
                paint.alpha = (a * 0.35f * 255f).toInt()
                paint.maskFilter = BlurMaskFilter((size * 0.5f).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
            }
            Tool.CHALK -> {
                paint.color = argb
                paint.strokeWidth = size * 1.4f
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (a * 0.75f * 255f).toInt()
                paint.pathEffect = DiscretePathEffect(1.5f, 3f)
            }
            Tool.PIXEL -> {
                // Hard, aliased edges for pixel art.
                paint.isAntiAlias = false
                paint.color = argb
                paint.strokeWidth = size.coerceAtLeast(1f)
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.MITER
                paint.alpha = (a * 255f).toInt()
            }
            Tool.FILL, Tool.LASSO, Tool.LINE, Tool.RECT, Tool.ELLIPSE, Tool.TEXT, Tool.EYEDROPPER -> {
                paint.color = argb
                paint.strokeWidth = size
                paint.alpha = (a * 255f).toInt()
            }
        }
        return paint
    }
}
