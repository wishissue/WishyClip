package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

object BitmapOps {
    private val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

    /** Replaces all pixels of [dst] with [src] (same size expected). */
    fun replace(dst: Bitmap, src: Bitmap) {
        Canvas(dst).drawBitmap(src, 0f, 0f, srcPaint)
    }

    /** Replaces pixels within [rect] of [dst] with [src]. */
    fun replaceRect(dst: Bitmap, src: Bitmap, rect: Rect) {
        Canvas(dst).drawBitmap(src, rect, rect, srcPaint)
    }

    fun clear(dst: Bitmap) {
        dst.eraseColor(0)
    }
}

/** Immutable copy of this bitmap. */
fun Bitmap.snapshot(): Bitmap = this.copy(Bitmap.Config.ARGB_8888, false)!!
