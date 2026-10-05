package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import org.wishyclip.app.model.Tool
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixels of one finished stroke from *before* it was applied; [rect] is where they belong. */
class StrokePatch(val bitmap: Bitmap, val rect: Rect)

/** What the canvas should draw on top of the active layer while a stroke is in progress. */
class StrokeOverlay(val bitmap: Bitmap, val dirty: Rect, val alpha: Float)

/** The in-progress stroke overlay plus the layer it belongs above. */
class LiveStroke(val layerId: Long, val overlay: StrokeOverlay)

/**
 * Incremental stroke renderer.
 *
 * The old renderer restored and redrew the *entire* stroke on every pointer event, so cost grew
 * with stroke length (long/fast strokes froze). This one only draws the newest curve segment
 * per event, so cost per event is constant:
 *
 *  - Brushes draw at full alpha onto a reusable scratch overlay; the canvas shows it above the
 *    active layer. On [finish] it is composited once with the brush alpha (no overlap darkening)
 *    and only the touched rectangle is saved for undo.
 *  - The eraser clears directly on the layer. Touched 128px cells are backed up lazily so undo
 *    needs only the cells that were actually erased, not a full-canvas copy.
 *  - Shapes (line/rect/ellipse) redraw only their own small dirty rect on the overlay.
 */
class StrokeRenderer {
    private var overlayBmp: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    private var target: Bitmap? = null
    private var targetCanvas: Canvas? = null
    private var tool = Tool.PEN
    private var size = 8f
    private var opacity = 1f
    private var paint = Paint()
    private var baseWidth = 8f
    private var viaOverlay = true
    private var isShape = false
    private var variablePressure = false

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var midX = 0f
    private var midY = 0f
    private var lastPressure = 1f

    private val dirty = Rect()
    private var hasDirty = false
    private val segPath = Path()

    private val backups = HashMap<Long, Bitmap>()

    val active: Boolean get() = target != null

    fun begin(
        target: Bitmap,
        tool: Tool,
        argb: Int,
        size: Float,
        opacity: Float,
        x: Float,
        y: Float,
        pressure: Float = 1f,
        @Suppress("UNUSED_PARAMETER") tilt: Float = 0f
    ) {
        cancel()
        this.target = target
        this.targetCanvas = Canvas(target)
        this.tool = tool
        this.size = size
        this.opacity = opacity
        isShape = tool == Tool.LINE || tool == Tool.RECT || tool == Tool.ELLIPSE
        viaOverlay = tool != Tool.ERASER
        paint = BrushPaints.create(tool, argb, size, opacity)
        dirty.setEmpty()
        hasDirty = false
        if (viaOverlay) {
            paint.alpha = 255
            ensureOverlay(target)
            BitmapOps.clearRect(overlayBmp!!, Rect(0, 0, target.width, target.height))
        }
        baseWidth = paint.strokeWidth
        variablePressure = !isShape && tool != Tool.MARKER && abs(pressure - 1f) > 0.05f
        startX = x; startY = y
        lastX = x; lastY = y
        midX = x; midY = y
        lastPressure = pressure
        if (!isShape) {
            applyPressureWidth(pressure, pressure)
            val margin = reach()
            if (!viaOverlay) backup(clamp(x - margin, y - margin, x + margin, y + margin))
            drawCanvas().drawPoint(x, y, paint)
            expandDirty(x - margin, y - margin, x + margin, y + margin)
        }
    }

    fun moveTo(x: Float, y: Float, pressure: Float = 1f, @Suppress("UNUSED_PARAMETER") tilt: Float = 0f) {
        if (target == null) return
        if (isShape) {
            updateShape(x, y)
            lastX = x; lastY = y
            return
        }
        val nmx = (x + lastX) / 2f
        val nmy = (y + lastY) / 2f
        applyPressureWidth(lastPressure, pressure)
        segPath.rewind()
        segPath.moveTo(midX, midY)
        segPath.quadTo(lastX, lastY, nmx, nmy)
        drawSegment(
            min(min(midX, lastX), nmx), min(min(midY, lastY), nmy),
            max(max(midX, lastX), nmx), max(max(midY, lastY), nmy)
        )
        midX = nmx; midY = nmy
        lastX = x; lastY = y
        lastPressure = pressure
    }

    /**
     * Commits the stroke to the target bitmap. Returns the pre-stroke pixels of the touched
     * rectangle for undo (ownership passes to the caller), or null if nothing was drawn.
     */
    fun finish(): StrokePatch? {
        val t = target ?: return null
        if (!isShape && (midX != lastX || midY != lastY)) {
            // Close the gap between the last curve midpoint and the final pointer position.
            segPath.rewind()
            segPath.moveTo(midX, midY)
            segPath.lineTo(lastX, lastY)
            drawSegment(min(midX, lastX), min(midY, lastY), max(midX, lastX), max(midY, lastY))
        }
        if (!hasDirty) {
            cancel()
            return null
        }
        val rect = Rect(dirty)
        if (viaOverlay) {
            val patch = BitmapOps.copyRect(t, rect)
            val overlay = overlayBmp!!
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            p.alpha = (BrushPaints.alphaFactor(tool, opacity) * 255f).roundToInt().coerceIn(0, 255)
            Canvas(t).drawBitmap(overlay, rect, rect, p)
            BitmapOps.clearRect(overlay, rect)
            reset()
            return StrokePatch(patch, rect)
        } else {
            val patchBmp = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            val c = Canvas(patchBmp)
            c.translate(-rect.left.toFloat(), -rect.top.toFloat())
            val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            for ((key, cell) in backups) {
                val cx = (key and 0xFFFFL).toInt()
                val cy = (key shr 16).toInt()
                c.drawBitmap(cell, (cx * CELL).toFloat(), (cy * CELL).toFloat(), srcPaint)
            }
            reset()
            return StrokePatch(patchBmp, rect)
        }
    }

    /** Abandons the stroke, restoring the target (eraser) and clearing the overlay. */
    fun cancel() {
        val t = target ?: return
        if (viaOverlay) {
            if (hasDirty) overlayBmp?.let { BitmapOps.clearRect(it, Rect(0, 0, it.width, it.height)) }
        } else {
            for ((key, cell) in backups) {
                val cx = (key and 0xFFFFL).toInt()
                val cy = (key shr 16).toInt()
                BitmapOps.putAt(t, cell, cx * CELL, cy * CELL)
            }
        }
        reset()
    }

    /** Overlay to draw above the active layer, or null when there is nothing to show. */
    fun overlay(): StrokeOverlay? {
        val o = overlayBmp ?: return null
        if (target == null || !viaOverlay || !hasDirty) return null
        return StrokeOverlay(o, dirty, BrushPaints.alphaFactor(tool, opacity))
    }

    /** Frees the scratch overlay. Call when the editor is destroyed. */
    fun release() {
        cancel()
        overlayBmp?.let { if (!it.isRecycled) it.recycle() }
        overlayBmp = null
        overlayCanvas = null
    }

    // ------------------------------------------------------------------ internals

    private fun reset() {
        dirty.setEmpty()
        hasDirty = false
        segPath.rewind()
        overlayBmp?.let { BitmapOps.clearRect(it, Rect(0, 0, it.width, it.height)) }
        for (b in backups.values) if (!b.isRecycled) b.recycle()
        backups.clear()
        target = null
        targetCanvas = null
    }

    private fun ensureOverlay(t: Bitmap) {
        val o = overlayBmp
        if (o == null || o.isRecycled || o.width != t.width || o.height != t.height) {
            o?.let { if (!it.isRecycled) it.recycle() }
            val fresh = Bitmap.createBitmap(t.width, t.height, Bitmap.Config.ARGB_8888)
            overlayBmp = fresh
            overlayCanvas = Canvas(fresh)
        }
    }

    private fun drawCanvas(): Canvas = if (viaOverlay) overlayCanvas!! else targetCanvas!!

    private fun applyPressureWidth(p1: Float, p2: Float) {
        paint.strokeWidth = if (variablePressure) {
            max(1f, baseWidth * (0.2f + 0.8f * ((p1 + p2) / 2f)))
        } else {
            baseWidth
        }
    }

    /** How far ink can reach beyond the geometry (stroke half-width, blur, antialiasing). */
    private fun reach(): Float = max(baseWidth, paint.strokeWidth) + size + 8f

    private fun drawSegment(l: Float, t: Float, r: Float, b: Float) {
        val m = reach()
        if (!viaOverlay) backup(clamp(l - m, t - m, r + m, b + m))
        drawCanvas().drawPath(segPath, paint)
        expandDirty(l - m, t - m, r + m, b + m)
    }

    private fun updateShape(x: Float, y: Float) {
        val overlay = overlayBmp ?: return
        if (hasDirty) BitmapOps.clearRect(overlay, dirty)
        val c = overlayCanvas ?: return
        val l = min(startX, x)
        val t = min(startY, y)
        val r = max(startX, x)
        val b = max(startY, y)
        when (tool) {
            Tool.LINE -> c.drawLine(startX, startY, x, y, paint)
            Tool.RECT -> c.drawRect(l, t, r, b, paint)
            else -> c.drawOval(l, t, r, b, paint)
        }
        val m = reach()
        val rect = clamp(l - m, t - m, r + m, b + m)
        dirty.set(rect)
        hasDirty = !rect.isEmpty
    }

    private fun clamp(l: Float, t: Float, r: Float, b: Float): Rect {
        val w = target!!.width
        val h = target!!.height
        return Rect(
            floor(l).toInt().coerceIn(0, w),
            floor(t).toInt().coerceIn(0, h),
            ceil(r).toInt().coerceIn(0, w),
            ceil(b).toInt().coerceIn(0, h)
        )
    }

    private fun expandDirty(l: Float, t: Float, r: Float, b: Float) {
        val rect = clamp(l, t, r, b)
        if (rect.isEmpty) return
        if (hasDirty) dirty.union(rect) else dirty.set(rect)
        hasDirty = true
    }

    /** Eraser only: remember the original pixels of every cell about to be touched. */
    private fun backup(rect: Rect) {
        val t = target ?: return
        if (rect.isEmpty) return
        val cx0 = rect.left / CELL
        val cy0 = rect.top / CELL
        val cx1 = (rect.right - 1) / CELL
        val cy1 = (rect.bottom - 1) / CELL
        for (cy in cy0..cy1) {
            for (cx in cx0..cx1) {
                val key = (cy.toLong() shl 16) or cx.toLong()
                if (backups.containsKey(key)) continue
                val cell = Rect(
                    cx * CELL, cy * CELL,
                    min((cx + 1) * CELL, t.width), min((cy + 1) * CELL, t.height)
                )
                backups[key] = BitmapOps.copyRect(t, cell)
            }
        }
    }

    private companion object {
        const val CELL = 128
    }
}
