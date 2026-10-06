package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.model.Tool
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
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

    private var mirror = MirrorMode.OFF
    private var dab: DabBrush? = null
    private var isDab = false
    private var dabCarry = 0f
    private var strokeAngle = 0f
    private val rnd = Random()
    private val dabPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

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
        @Suppress("UNUSED_PARAMETER") tilt: Float = 0f,
        mirror: MirrorMode = MirrorMode.OFF,
        dab: DabBrush? = null
    ) {
        cancel()
        reset()
        this.target = target
        this.targetCanvas = Canvas(target)
        this.tool = tool
        this.size = size
        this.opacity = opacity
        isShape = tool == Tool.LINE || tool == Tool.RECT || tool == Tool.ELLIPSE
        viaOverlay = tool != Tool.ERASER
        this.mirror = if (tool == Tool.LASSO) MirrorMode.OFF else mirror
        this.dab = if (tool == Tool.CUSTOM) dab else null
        isDab = this.dab != null
        dabCarry = 0f
        strokeAngle = 0f
        rnd.setSeed(x.toBits().toLong() xor (y.toBits().toLong() shl 20))
        this.dab?.let {
            dabPaint.color = argb
            dabPaint.alpha = (it.flow * 255f).roundToInt().coerceIn(1, 255)
        }
        paint = BrushPaints.create(tool, argb, size, opacity)
        dirty.setEmpty()
        hasDirty = false
        if (viaOverlay) {
            paint.alpha = 255
            ensureOverlay(target)
            BitmapOps.clear(overlayBmp!!)
        }
        baseWidth = paint.strokeWidth
        variablePressure = !isShape && tool != Tool.MARKER && abs(pressure - 1f) > 0.05f
        startX = x; startY = y
        lastX = x; lastY = y
        midX = x; midY = y
        lastPressure = pressure
        if (!isShape) {
            if (isDab) {
                stampAt(x, y, pressure)
            } else {
                applyPressureWidth(pressure, pressure)
                val margin = reach()
                val prevStyle = paint.style
                paint.style = Paint.Style.FILL
                val tw = target.width.toFloat()
                val th = target.height.toFloat()
                forEachMirror { fx, fy ->
                    val mx = if (fx) tw - x else x
                    val my = if (fy) th - y else y
                    if (!viaOverlay) backup(clamp(mx - margin, my - margin, mx + margin, my + margin))
                    drawCanvas().drawCircle(mx, my, paint.strokeWidth / 2f, paint)
                    expandDirty(mx - margin, my - margin, mx + margin, my + margin)
                }
                paint.style = prevStyle
            }
        }
    }

    fun moveTo(x: Float, y: Float, pressure: Float = 1f, @Suppress("UNUSED_PARAMETER") tilt: Float = 0f) {
        if (target == null) return
        if (isShape) {
            updateShape(x, y)
            lastX = x; lastY = y
            return
        }
        if (isDab) {
            dabSegment(x, y, pressure)
            lastX = x; lastY = y
            midX = x; midY = y
            lastPressure = pressure
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
            BitmapOps.clear(overlay)
            reset()
            return StrokePatch(patch, rect)
        } else {
            val patch = BitmapOps.copyRect(t, rect)
            val c = Canvas(patch)
            val srcPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            for ((key, cell) in backups) {
                val cx = (key and 0xFFFFL).toInt()
                val cy = (key shr 16).toInt()
                c.drawBitmap(cell, (cx * CELL - rect.left).toFloat(), (cy * CELL - rect.top).toFloat(), srcPaint)
            }
            reset()
            return StrokePatch(patch, rect)
        }
    }

    /** Abandons the stroke, restoring the target (eraser) and clearing the overlay. */
    fun cancel() {
        val t = target
        if (t != null) {
            if (viaOverlay) {
                if (hasDirty) overlayBmp?.let { BitmapOps.clearRect(it, Rect(0, 0, it.width, it.height)) }
            } else {
                for ((key, cell) in backups) {
                    val cx = (key and 0xFFFFL).toInt()
                    val cy = (key shr 16).toInt()
                    BitmapOps.putAt(t, cell, cx * CELL, cy * CELL)
                }
            }
        }
        reset()
    }

    /** Overlay to draw above the active layer, or null when there is nothing to show. */
    fun overlay(): StrokeOverlay? {
        val o = overlayBmp ?: return null
        if (target == null || !viaOverlay || !hasDirty) return null
        return StrokeOverlay(o, Rect(dirty), BrushPaints.alphaFactor(tool, opacity))
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
        overlayBmp?.let { BitmapOps.clear(it) }
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
        } else {
            overlayCanvas?.setBitmap(o) ?: run { overlayCanvas = Canvas(o) }
            BitmapOps.clear(o)
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
        val w = target!!.width.toFloat()
        val h = target!!.height.toFloat()
        forEachMirror { fx, fy ->
            val ml = if (fx) w - r else l
            val mr = if (fx) w - l else r
            val mt = if (fy) h - b else t
            val mb = if (fy) h - t else b
            if (!viaOverlay) backup(clamp(ml - m, mt - m, mr + m, mb + m))
            val c = drawCanvas()
            if (fx || fy) {
                c.save()
                c.scale(if (fx) -1f else 1f, if (fy) -1f else 1f, w / 2f, h / 2f)
                c.drawPath(segPath, paint)
                c.restore()
            } else {
                c.drawPath(segPath, paint)
            }
            expandDirty(ml - m, mt - m, mr + m, mb + m)
        }
    }

    /** Runs [block] once for the stroke itself and once per active mirror image of it. */
    private inline fun forEachMirror(block: (Boolean, Boolean) -> Unit) {
        block(false, false)
        if (mirror.flipX) block(true, false)
        if (mirror.flipY) block(false, true)
        if (mirror.flipX && mirror.flipY) block(true, true)
    }

    private fun dabSegment(x: Float, y: Float, pressure: Float) {
        val d = dab ?: return
        val dx = x - lastX
        val dy = y - lastY
        val len = hypot(dx, dy)
        if (len < 0.001f) return
        strokeAngle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val step = max(1f, d.spacing * size)
        var traveled = step - dabCarry
        while (traveled <= len) {
            val k = traveled / len
            stampAt(lastX + dx * k, lastY + dy * k, lastPressure + (pressure - lastPressure) * k)
            traveled += step
        }
        dabCarry = len - (traveled - step)
    }

    /** Stamps one tip (and its mirror images) centred on (x, y). */
    private fun stampAt(x: Float, y: Float, pressure: Float) {
        val d = dab ?: return
        val t = target ?: return
        val c = drawCanvas()
        val pressureScale = if (variablePressure) 0.2f + 0.8f * pressure.coerceIn(0f, 1f) else 1f
        val jitter = if (d.sizeJitter > 0f) 1f - d.sizeJitter * rnd.nextFloat() else 1f
        val diameter = max(1f, size * pressureScale * jitter)
        val scatterPx = d.scatter * size
        val jx = if (scatterPx > 0f) (rnd.nextFloat() * 2f - 1f) * scatterPx else 0f
        val jy = if (scatterPx > 0f) (rnd.nextFloat() * 2f - 1f) * scatterPx else 0f
        val baseAngle = d.angle + (if (d.rotateWithStroke) strokeAngle else 0f)
        val tw = t.width.toFloat()
        val th = t.height.toFloat()
        val scale = diameter / max(d.tip.width, d.tip.height).toFloat()
        val reachPx = diameter * 0.75f + scatterPx + 4f
        forEachMirror { fx, fy ->
            var px = x + jx
            var py = y + jy
            var ang = baseAngle
            if (fx) { px = tw - px; ang = 180f - ang }
            if (fy) { py = th - py; ang = -ang }
            c.save()
            c.translate(px, py)
            c.rotate(ang)
            c.scale(scale, scale)
            c.drawBitmap(d.tip, -d.tip.width / 2f, -d.tip.height / 2f, dabPaint)
            c.restore()
            expandDirty(px - reachPx, py - reachPx, px + reachPx, py + reachPx)
        }
    }

    private fun updateShape(x: Float, y: Float) {
        val overlay = overlayBmp ?: return
        if (hasDirty) BitmapOps.clear(overlay)
        val c = overlayCanvas ?: return
        val w = overlay.width.toFloat()
        val h = overlay.height.toFloat()
        val l = min(startX, x)
        val t = min(startY, y)
        val r = max(startX, x)
        val b = max(startY, y)
        val m = reach()
        dirty.setEmpty()
        hasDirty = false
        forEachMirror { fx, fy ->
            c.save()
            if (fx || fy) c.scale(if (fx) -1f else 1f, if (fy) -1f else 1f, w / 2f, h / 2f)
            when (tool) {
                Tool.LINE -> c.drawLine(startX, startY, x, y, paint)
                Tool.RECT -> c.drawRect(l, t, r, b, paint)
                else -> c.drawOval(l, t, r, b, paint)
            }
            c.restore()
            val ml = if (fx) w - r else l
            val mr = if (fx) w - l else r
            val mt = if (fy) h - b else t
            val mb = if (fy) h - t else b
            expandDirty(ml - m, mt - m, mr + m, mb + m)
        }
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
