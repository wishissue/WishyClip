package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** Radius (in layer pixels) within which a touch grabs a handle. */
fun selectionHandleSlop(projectWidth: Int): Float = projectWidth * 0.04f

/** How far above the top edge the rotate handle floats (layer pixels). */
fun selectionRotateOffset(projectWidth: Int): Float = projectWidth * 0.07f

/** Editable source of a floating text object. The bitmap is re-rendered from this. */
class TextSpec(var text: String, var sizePx: Float, var fontName: String = "Inter")

enum class SelectionHit { NONE, MOVE, SCALE, ROTATE }

/**
 * A floating, transformable piece of art: either pixels lifted out of a layer with the lasso,
 * or a text object. Nothing touches the layer until it is committed.
 *
 * [liftBefore]/[liftRect] hold the layer's original pixels under a lasso lift so that cancel and
 * undo can restore them exactly.
 */
class LassoSelection(
    var pixels: Bitmap,
    var bounds: RectF,
    val outline: Path,
    var liftBefore: Bitmap? = null,
    val liftRect: Rect? = null,
    val text: TextSpec? = null
) {
    var translateX: Float = 0f
    var translateY: Float = 0f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var rotation: Float = 0f

    val isText: Boolean get() = text != null

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

    /**
     * Layer-space handle positions: TL, TR, BR, BL corners (indices 0..7), rotate handle (8,9),
     * and center (10,11).
     */
    fun handlePoints(rotateOffset: Float): FloatArray {
        val w = pixels.width.toFloat()
        val h = pixels.height.toFloat()
        val pts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h, w / 2f, 0f, w / 2f, h / 2f)
        getMatrix().mapPoints(pts)
        val tx = pts[8]
        val ty = pts[9]
        val cx = pts[10]
        val cy = pts[11]
        val len = hypot(tx - cx, ty - cy).coerceAtLeast(0.001f)
        val dx = (tx - cx) / len
        val dy = (ty - cy) / len
        return floatArrayOf(
            pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7],
            tx + dx * rotateOffset, ty + dy * rotateOffset,
            cx, cy
        )
    }

    fun center(rotateOffset: Float): Pair<Float, Float> {
        val hp = handlePoints(rotateOffset)
        return hp[10] to hp[11]
    }

    fun hitTest(x: Float, y: Float, slop: Float, rotateOffset: Float): SelectionHit {
        val hp = handlePoints(rotateOffset)
        if (hypot(x - hp[8], y - hp[9]) <= slop) return SelectionHit.ROTATE
        for (i in 0 until 4) {
            if (hypot(x - hp[i * 2], y - hp[i * 2 + 1]) <= slop) return SelectionHit.SCALE
        }
        val inv = Matrix()
        if (!getMatrix().invert(inv)) return SelectionHit.NONE
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        val pad = slop * 0.3f
        val inside = p[0] >= -pad && p[0] <= pixels.width + pad &&
            p[1] >= -pad && p[1] <= pixels.height + pad
        return if (inside) SelectionHit.MOVE else SelectionHit.NONE
    }

    fun release() {
        if (!pixels.isRecycled) pixels.recycle()
        liftBefore?.let { if (!it.isRecycled) it.recycle() }
        liftBefore = null
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

    /** The loop being drawn (layer space), for a live preview. */
    val currentPath: Path get() = path

    override fun begin(x: Float, y: Float) {
        path.reset()
        path.moveTo(x, y)
    }

    override fun addPoint(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    override fun end(layer: Bitmap): LassoSelection? {
        path.close()
        val raw = RectF()
        path.computeBounds(raw, true)

        val width = ceil(raw.width()).toInt()
        val height = ceil(raw.height()).toInt()
        if (width <= 1 || height <= 1) return null

        // Keep bounds exactly the size of the floating bitmap so the transform pivot is exact.
        val bounds = RectF(raw.left, raw.top, raw.left + width, raw.top + height)

        val lift = Rect(
            floor(bounds.left).toInt().coerceIn(0, layer.width),
            floor(bounds.top).toInt().coerceIn(0, layer.height),
            ceil(bounds.right).toInt().coerceIn(0, layer.width),
            ceil(bounds.bottom).toInt().coerceIn(0, layer.height)
        )
        if (lift.isEmpty) return null

        // Remember the original pixels so cancel / undo can restore them.
        val before = BitmapOps.copyRect(layer, lift)

        val floating = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val floatingCanvas = Canvas(floating)
        val localPath = Path(path)
        localPath.offset(-bounds.left, -bounds.top)
        floatingCanvas.clipPath(localPath)
        floatingCanvas.drawBitmap(layer, -bounds.left, -bounds.top, null)

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        Canvas(layer).drawPath(path, clearPaint)

        return LassoSelection(floating, bounds, Path(path), liftBefore = before, liftRect = lift)
    }
}
