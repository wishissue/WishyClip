package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Renders (multi-line) text into a tightly sized transparent bitmap. */
object TextRaster {
    private const val MAX_DIM = 4096

    fun render(text: String, argb: Int, sizePx: Float, opacity: Float): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = sizePx.coerceIn(8f, 1500f)
        paint.color = argb
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()

        val safe = text.ifEmpty { " " }
        val widest = safe.split("\n").maxOf { paint.measureText(it) }
        val textWidth = ceil(widest).toInt().coerceIn(1, MAX_DIM)
        val layout = StaticLayout.Builder
            .obtain(safe, 0, safe.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

        val pad = (paint.textSize * 0.15f).toInt().coerceAtLeast(4)
        val w = (textWidth + pad * 2).coerceAtMost(MAX_DIM)
        val h = (layout.height + pad * 2).coerceIn(1, MAX_DIM)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)
        return bmp
    }
}
