package org.wishyclip.app.canvas

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.design.WishyIcons
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val ACCENT = 0xFF4C8DFF.toInt()

/**
 * Draws the project's layers (fit to view, letterboxed) and turns pointer events into stroke
 * callbacks with pressure, stylus palm rejection and low-latency historical samples.
 *
 * Performance notes: all Paint/Matrix/Rect objects are allocated once (not per frame), the stroke
 * in progress is drawn from a small overlay instead of a rewritten layer, and the pointer handler
 * is *not* keyed on zoom/pan so pinching never restarts it mid-gesture.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    projectWidth: Int,
    projectHeight: Int,
    layers: () -> List<LayerData>,
    onionSkinData: () -> OnionSkinData? = { null },
    activeLassoSelection: () -> LassoSelection? = { null },
    strokeOverlay: () -> LiveStroke? = { null },
    lassoPreview: () -> Path? = { null },
    revision: () -> Int,
    enabled: Boolean,
    view: CanvasViewState = remember { CanvasViewState() },
    mirror: () -> MirrorMode = { MirrorMode.OFF },
    ruler: () -> RulerState? = { null },
    onRulerChange: (RulerState) -> Unit = {},
    onStrokeStart: (Float, Float, Float, Float) -> Unit,
    onStrokeMove: (Float, Float, Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onStrokeCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val startCb by rememberUpdatedState(onStrokeStart)
    val moveCb by rememberUpdatedState(onStrokeMove)
    val endCb by rememberUpdatedState(onStrokeEnd)
    val rulerCb by rememberUpdatedState(ruler)
    val rulerChangeCb by rememberUpdatedState(onRulerChange)
    val mirrorCb by rememberUpdatedState(mirror)
    val cancelCb by rememberUpdatedState(onStrokeCancel)
    val density = LocalDensity.current.density

    // Reused every frame instead of being reallocated.
    val bgPaint = remember { Paint().apply { color = android.graphics.Color.WHITE } }
    val layerPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val ghostPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val selPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val tintFilters = remember { HashMap<Int, PorterDuffColorFilter>() }
    val dashEffect = remember { DashPathEffect(floatArrayOf(14f, 10f), 0f) }
    val outlinePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ACCENT }
    }
    val handleFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE }
    }
    val rulerBarPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = ACCENT; alpha = 70
        }
    }
    val rulerEdgePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = ACCENT }
    }
    val dst = remember { RectF() }
    val srcRect = remember { Rect() }
    val selMatrix = remember { Matrix() }
    val scratchPath = remember { Path() }
    val scratchMatrix = remember { Matrix() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD9D9D9))
                .onSizeChanged { newSize ->
                    val old = viewSize
                    if (old.width > 0 && old.height > 0 && newSize.width > 0 && newSize.height > 0 &&
                        old != newSize && projectWidth > 0 && projectHeight > 0
                    ) {
                        // Device rotated / window resized: keep the same part of the canvas in view.
                        val oldFit = min(old.width.toFloat() / projectWidth, old.height.toFloat() / projectHeight)
                        val newFit = min(newSize.width.toFloat() / projectWidth, newSize.height.toFloat() / projectHeight)
                        view.rescalePan(newFit / oldFit)
                    }
                    viewSize = newSize
                }
                // NOTE: not keyed on zoom/pan/rotation. Those are read live inside map(); keying on
                // them restarted this block on every pinch step and cancelled the gesture.
                .pointerInput(viewSize, enabled, projectWidth, projectHeight) {
                    if (!enabled || viewSize.width == 0 || viewSize.height == 0) return@pointerInput
                    val scale = min(
                        viewSize.width.toFloat() / projectWidth,
                        viewSize.height.toFloat() / projectHeight
                    )
                    val ox = (viewSize.width - projectWidth * scale) / 2f
                    val oy = (viewSize.height - projectHeight * scale) / 2f
                    val cx = viewSize.width / 2f
                    val cy = viewSize.height / 2f

                    // Inverse of the draw transform: translate(center+pan) rotate scale translate(-center).
                    fun map(p: Offset): Offset {
                        val dx = p.x - cx - view.pan.x
                        val dy = p.y - cy - view.pan.y
                        val rad = Math.toRadians(-view.rotation.toDouble())
                        val c = cos(rad).toFloat()
                        val s = sin(rad).toFloat()
                        val vx = (dx * c - dy * s) / view.zoom + cx
                        val vy = (dx * s + dy * c) / view.zoom + cy
                        return Offset((vx - ox) / scale, (vy - oy) / scale)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val primaryId = down.id
                        val stylus = down.type == PointerType.Stylus

                        val rawP0 = map(down.position)
                        val rl = rulerCb()
                        val fit = scale * view.zoom
                        val grab = rl?.hit(rawP0.x, rawP0.y, 26f * density / fit) ?: RulerHandle.NONE
                        if (rl != null && grab != RulerHandle.NONE) {
                            // Dragging a ruler handle: move/rotate the ruler instead of drawing.
                            down.consume()
                            var cur: RulerState = rl
                            var last = rawP0
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == primaryId }
                                if (ch == null || !ch.pressed) {
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                                val np = map(ch.position)
                                cur = cur.drag(grab, np.x - last.x, np.y - last.y)
                                last = np
                                rulerChangeCb(cur)
                                event.changes.forEach { it.consume() }
                            }
                            return@awaitEachGesture
                        }
                        // A stroke that starts beside the ruler stays locked to its edge.
                        val lockedRuler = if (rl != null && rl.distanceTo(rawP0.x, rawP0.y) <= 32f * density / fit) rl else null
                        val snapOut = FloatArray(2)
                        fun snap(p: Offset): Offset {
                            val r = lockedRuler ?: return p
                            r.project(p.x, p.y, snapOut)
                            return Offset(snapOut[0], snapOut[1])
                        }
                        val p0 = snap(rawP0)
                        startCb(p0.x, p0.y, down.pressure, 0f)
                        down.consume()

                        var transforming = false
                        while (true) {
                            val event = awaitPointerEvent()

                            // With a stylus, any other contact is a resting palm: ignore it.
                            val pressedCount = if (stylus) {
                                if (event.changes.any { it.id == primaryId && it.pressed }) 1 else 0
                            } else {
                                event.changes.count { it.pressed }
                            }
                            if (pressedCount == 0) {
                                event.changes.forEach { it.consume() }
                                break
                            }

                            if (transforming || pressedCount > 1) {
                                if (!transforming) {
                                    transforming = true
                                    // Second finger: this was a pinch, not a stroke. Discard the dot.
                                    (cancelCb ?: endCb)()
                                }
                                if (pressedCount > 1) {
                                    val s = event.calculateZoom()
                                    val dRot = event.calculateRotation()
                                    val dPan = event.calculatePan()
                                    val pivot = event.calculateCentroid(useCurrent = false)
                                    if (pivot != Offset.Unspecified) {
                                        val newZoom = (view.zoom * s).coerceIn(0.2f, 8f)
                                        val sEff = newZoom / view.zoom
                                        // Keep the point under the fingers fixed while zooming/rotating.
                                        val tx = cx + view.pan.x - pivot.x
                                        val ty = cy + view.pan.y - pivot.y
                                        val rad = Math.toRadians(dRot.toDouble())
                                        val c = cos(rad).toFloat()
                                        val sn = sin(rad).toFloat()
                                        val nx = pivot.x + sEff * (tx * c - ty * sn) + dPan.x
                                        val ny = pivot.y + sEff * (tx * sn + ty * c) + dPan.y
                                        view.pan = Offset(nx - cx, ny - cy)
                                        view.zoom = newZoom
                                        view.rotation += dRot
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == primaryId && it.pressed }
                            if (change == null) break

                            for (h in change.historical) {
                                val hp = snap(map(h.position))
                                moveCb(hp.x, hp.y, change.pressure, 0f)
                            }
                            val p = snap(map(change.position))
                            moveCb(p.x, p.y, change.pressure, 0f)
                            event.changes.forEach { it.consume() }
                        }
                        if (!transforming) endCb()
                    }
                }
        ) {
            revision()
            val scale = min(size.width / projectWidth, size.height / projectHeight)
            val dw = projectWidth * scale
            val dh = projectHeight * scale
            val ox = (size.width - dw) / 2f
            val oy = (size.height - dh) / 2f
            val viewCenterX = size.width / 2f
            val viewCenterY = size.height / 2f

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.translate(viewCenterX + view.pan.x, viewCenterY + view.pan.y)
                nativeCanvas.rotate(view.rotation)
                nativeCanvas.scale(view.zoom, view.zoom)
                nativeCanvas.translate(-viewCenterX, -viewCenterY)

                dst.set(ox, oy, ox + dw, oy + dh)
                nativeCanvas.drawRect(dst, bgPaint)

                // Onion skin ghosts
                val onion = onionSkinData()
                if (onion != null && onion.settings.enabled) {
                    fun drawGhosts(ghosts: List<GhostFrame>, tint: Int) {
                        ghostPaint.colorFilter = if (tint != 0) {
                            tintFilters.getOrPut(tint) { PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) }
                        } else null
                        for (ghost in ghosts) {
                            val factor = (1f - (ghost.distance - 1) * 0.25f).coerceAtLeast(0.2f)
                            val alpha = (onion.settings.opacity * factor).coerceIn(0.01f, 1f)
                            for (layer in ghost.layers) {
                                if (!layer.visible) continue
                                ghostPaint.alpha = (layer.opacity * alpha * 255f).toInt().coerceIn(0, 255)
                                nativeCanvas.drawBitmap(layer.bitmap, null, dst, ghostPaint)
                            }
                        }
                    }
                    drawGhosts(onion.before, onion.settings.tintBefore)
                    drawGhosts(onion.after, onion.settings.tintAfter)
                }

                // Current frame layers, with the live stroke sitting directly above its layer.
                val live = strokeOverlay()
                for (layer in layers()) {
                    if (!layer.visible) continue
                    layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                    LayerBlending.apply(layerPaint, layer.blendMode)
                    nativeCanvas.drawBitmap(layer.bitmap, null, dst, layerPaint)
                    layerPaint.xfermode = null
                    if (live != null && live.layerId == layer.id) {
                        val o = live.overlay
                        srcRect.set(o.dirty)
                        val d = o.dirty
                        dst.set(
                            ox + d.left * scale, oy + d.top * scale,
                            ox + d.right * scale, oy + d.bottom * scale
                        )
                        layerPaint.alpha = (layer.opacity * o.alpha * 255f).toInt().coerceIn(0, 255)
                        nativeCanvas.drawBitmap(o.bitmap, srcRect, dst, layerPaint)
                        dst.set(ox, oy, ox + dw, oy + dh)
                    }
                }

                // Mirror axes
                val mirrorMode = mirrorCb()
                if (mirrorMode != MirrorMode.OFF) {
                    outlinePaint.strokeWidth = 1.5f * density / view.zoom
                    outlinePaint.pathEffect = dashEffect
                    if (mirrorMode.flipX) nativeCanvas.drawLine(ox + dw / 2f, oy, ox + dw / 2f, oy + dh, outlinePaint)
                    if (mirrorMode.flipY) nativeCanvas.drawLine(ox, oy + dh / 2f, ox + dw, oy + dh / 2f, outlinePaint)
                    outlinePaint.pathEffect = null
                }

                // Ruler: a translucent bar with a crisp edge and three handles
                val rl = rulerCb()
                if (rl != null) {
                    val px = density / view.zoom
                    val ax = ox + rl.ax * scale
                    val ay = oy + rl.ay * scale
                    val bx = ox + rl.bx * scale
                    val by = oy + rl.by * scale
                    rulerBarPaint.strokeWidth = 22f * px
                    nativeCanvas.drawLine(ax, ay, bx, by, rulerBarPaint)
                    rulerEdgePaint.strokeWidth = 2.5f * px
                    nativeCanvas.drawLine(ax, ay, bx, by, rulerEdgePaint)
                    outlinePaint.strokeWidth = 2.5f * px
                    outlinePaint.pathEffect = null
                    val hr = 13f * px
                    for ((hx, hy) in listOf(ax to ay, bx to by, (ax + bx) / 2f to (ay + by) / 2f)) {
                        nativeCanvas.drawCircle(hx, hy, hr, handleFill)
                        nativeCanvas.drawCircle(hx, hy, hr, outlinePaint)
                    }
                }

                // Lasso loop preview while drawing the selection
                val preview = lassoPreview()
                if (preview != null) {
                    scratchPath.set(preview)
                    scratchMatrix.setScale(scale, scale)
                    scratchMatrix.postTranslate(ox, oy)
                    scratchPath.transform(scratchMatrix)
                    outlinePaint.strokeWidth = 2.5f * density / view.zoom
                    outlinePaint.pathEffect = dashEffect
                    nativeCanvas.drawPath(scratchPath, outlinePaint)
                }

                // Floating selection / text with transform handles
                val sel = activeLassoSelection()
                if (sel != null && !sel.pixels.isRecycled) {
                    selMatrix.set(sel.getMatrix())
                    selMatrix.postScale(scale, scale)
                    selMatrix.postTranslate(ox, oy)
                    nativeCanvas.drawBitmap(sel.pixels, selMatrix, selPaint)

                    val hp = sel.handlePoints(selectionRotateOffset(projectWidth))
                    fun vx(i: Int) = ox + hp[i] * scale
                    fun vy(i: Int) = oy + hp[i] * scale
                    scratchPath.rewind()
                    scratchPath.moveTo(vx(0), vy(1))
                    scratchPath.lineTo(vx(2), vy(3))
                    scratchPath.lineTo(vx(4), vy(5))
                    scratchPath.lineTo(vx(6), vy(7))
                    scratchPath.close()
                    outlinePaint.strokeWidth = 2f * density / view.zoom
                    outlinePaint.pathEffect = dashEffect
                    nativeCanvas.drawPath(scratchPath, outlinePaint)

                    // stem from the top edge up to the rotate handle
                    val topMidX = (vx(0) + vx(2)) / 2f
                    val topMidY = (vy(1) + vy(3)) / 2f
                    outlinePaint.pathEffect = null
                    nativeCanvas.drawLine(topMidX, topMidY, vx(8), vy(9), outlinePaint)

                    val r = selectionHandleSlop(projectWidth) * 0.38f * scale
                    outlinePaint.strokeWidth = 2.5f * density / view.zoom
                    for (i in intArrayOf(0, 2, 4, 6, 8)) {
                        nativeCanvas.drawCircle(vx(i), vy(i + 1), r, handleFill)
                        nativeCanvas.drawCircle(vx(i), vy(i + 1), r, outlinePaint)
                    }
                }

                nativeCanvas.restore()
            }
        }

        if (view.isTransformed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(
                        iconRes = WishyIcons.RotateLeft,
                        contentDescription = "Rotate canvas 90 degrees left",
                        onClick = { view.rotateBy(-90f) }
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.RotateRight,
                        contentDescription = "Rotate canvas 90 degrees right",
                        onClick = { view.rotateBy(90f) }
                    )
                    TextButton(onClick = { view.reset() }) {
                        Text("Reset view", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
