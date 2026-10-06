package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

object BitmapOps {
    private val replacePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /** Replaces all pixels of [dst] with [src] (same size expected). */
    fun replace(dst: Bitmap, src: Bitmap) {
        Canvas(dst).drawBitmap(src, 0f, 0f, replacePaint)
    }

    /** Replaces pixels within [rect] of [dst] with [src]. */
    fun replaceRect(dst: Bitmap, src: Bitmap, rect: Rect) {
        Canvas(dst).drawBitmap(src, rect, rect, replacePaint)
    }

    /** Copies just [rect] of [src] into a new, tightly sized bitmap. */
    fun copyRect(src: Bitmap, rect: Rect): Bitmap {
        val out = Bitmap.createBitmap(rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, rect, Rect(0, 0, out.width, out.height), replacePaint)
        return out
    }

    /** Writes [patch] into [dst] with its top-left corner at ([left], [top]), replacing pixels. */
    fun putAt(dst: Bitmap, patch: Bitmap, left: Int, top: Int) {
        Canvas(dst).drawBitmap(patch, left.toFloat(), top.toFloat(), replacePaint)
    }

    /** Makes [rect] of [dst] fully transparent. */
    fun clearRect(dst: Bitmap, rect: Rect) {
        Canvas(dst).drawRect(rect, clearPaint)
    }

    fun clear(dst: Bitmap) {
        dst.eraseColor(0)
    }
}

/** Immutable copy of this bitmap. */
fun Bitmap.snapshot(): Bitmap = this.copy(Bitmap.Config.ARGB_8888, false)!!
