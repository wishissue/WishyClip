package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

class LassoSelection(
    val pixels: Bitmap,
    val bounds: RectF,
    val outline: Path
) {
    var translateX: Float = 0f
    var translateY: Float = 0f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var rotation: Float = 0f

    fun getMatrix(): Matrix {
        val m = Matrix()
        val centerX = bounds.width() / 2f
        val centerY = bounds.height() / 2f
        val targetLeft = bounds.left + translateX
        val targetTop = bounds.top + translateY
        val pivotX = targetLeft + centerX
        val pivotY = targetTop + centerY

        m.postTranslate(targetLeft, targetTop)
        m.postScale(scaleX, scaleY, pivotX, pivotY)
        m.postRotate(rotation, pivotX, pivotY)
        return m
    }
}

interface LassoTool {
    fun begin(x: Float, y: Float)
    fun addPoint(x: Float, y: Float)
    /** Closes the selection; returns the selected region as a floating bitmap, or null if empty. */
    fun end(layer: Bitmap): LassoSelection?
}

class StandardLassoTool : LassoTool {
    private val path = Path()

    override fun begin(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
    }

    override fun addPoint(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    override fun end(layer: Bitmap): LassoSelection? {
        path.close()
        val bounds = RectF()
        path.computeBounds(bounds, true)

        val width = bounds.width().toInt().coerceAtLeast(1)
        val height = bounds.height().toInt().coerceAtLeast(1)

        if (width <= 1 || height <= 1) return null

        val floating = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val floatingCanvas = Canvas(floating)

        val localPath = Path(path)
        localPath.offset(-bounds.left, -bounds.top)

        floatingCanvas.clipPath(localPath)
        floatingCanvas.drawBitmap(layer, -bounds.left, -bounds.top, null)

        val layerCanvas = Canvas(layer)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        layerCanvas.drawPath(path, clearPaint)

        return LassoSelection(floating, bounds, path)
    }
}
