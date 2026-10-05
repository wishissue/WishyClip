package org.wishyclip.app.canvas

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the project's layers (fit to view, letterboxed) and turns pointer events
 * into stroke callbacks with pressure sensitivity, stylus palm rejection, and low-latency historical processing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    projectWidth: Int,
    projectHeight: Int,
    layers: () -> List<LayerData>,
    onionSkinData: () -> OnionSkinData? = { null },
    activeLassoSelection: () -> LassoSelection? = { null },
    revision: () -> Int,
    enabled: Boolean,
    onStrokeStart: (Float, Float, Float, Float) -> Unit,
    onStrokeMove: (Float, Float, Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var isFlippedHorizontally by remember { mutableStateOf(false) }
    var isFlippedVertically by remember { mutableStateOf(false) }

    val startCb by rememberUpdatedState(onStrokeStart)
    val moveCb by rememberUpdatedState(onStrokeMove)
    val endCb by rememberUpdatedState(onStrokeEnd)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD9D9D9))
                .onSizeChanged { viewSize = it }
                .pointerInput(viewSize, enabled, projectWidth, projectHeight, zoom, pan, rotation, isFlippedHorizontally, isFlippedVertically) {
                    if (!enabled || viewSize.width == 0 || viewSize.height == 0) return@pointerInput
                    val scale = min(
                        viewSize.width.toFloat() / projectWidth,
                        viewSize.height.toFloat() / projectHeight
                    )
                    val ox = (viewSize.width - projectWidth * scale) / 2f
                    val oy = (viewSize.height - projectHeight * scale) / 2f

                    fun map(p: Offset): Offset {
                        val matrix = Matrix()
                        val viewCenterX = viewSize.width / 2f
                        val viewCenterY = viewSize.height / 2f

                        matrix.postTranslate(-viewCenterX, -viewCenterY)
                        matrix.postRotate(-rotation)
                        matrix.postScale(
                            if (isFlippedHorizontally) -1f / zoom else 1f / zoom,
                            if (isFlippedVertically) -1f / zoom else 1f / zoom
                        )
                        matrix.postTranslate(viewCenterX - pan.x, viewCenterY - pan.y)
                        matrix.postTranslate(-ox, -oy)
                        matrix.postScale(1f / scale, 1f / scale)

                        val pts = floatArrayOf(p.x, p.y)
                        matrix.mapPoints(pts)
                        return Offset(pts[0], pts[1])
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val p0 = map(down.position)
                        val p0Pressure = down.pressure

                        startCb(p0.x, p0.y, p0Pressure, 0f)
                        down.consume()

                        val primaryId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                val change1 = event.changes[0]
                                val change2 = event.changes[1]
                                val oldDist = (change1.previousPosition - change2.previousPosition).getDistance()
                                val newDist = (change1.position - change2.position).getDistance()
                                if (oldDist > 0f) {
                                    zoom = (zoom * (newDist / oldDist)).coerceIn(0.2f, 8f)
                                }
                                val panDelta = change1.position - change1.previousPosition
                                pan += panDelta
                                event.changes.forEach { it.consume() }
                                break
                            }

                            val change = event.changes.firstOrNull { it.id == primaryId }

                            if (change == null || !change.pressed) {
                                change?.consume()
                                break
                            }

                            for (h in change.historical) {
                                val hp = map(h.position)
                                moveCb(hp.x, hp.y, change.pressure, 0f)
                            }

                            val p = map(change.position)
                            moveCb(p.x, p.y, change.pressure, 0f)
                            change.consume()
                        }
                        endCb()
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
                nativeCanvas.translate(viewCenterX + pan.x, viewCenterY + pan.y)
                nativeCanvas.rotate(rotation)
                nativeCanvas.scale(
                    if (isFlippedHorizontally) -zoom else zoom,
                    if (isFlippedVertically) -zoom else zoom
                )
                nativeCanvas.translate(-viewCenterX, -viewCenterY)

                val bgPaint = Paint().apply { color = android.graphics.Color.WHITE }
                nativeCanvas.drawRect(ox, oy, ox + dw, oy + dh, bgPaint)

                // Draw Onion Skin ghost frames
                val onion = onionSkinData()
                if (onion != null && onion.settings.enabled) {
                    val drawGhostGroup = { ghosts: List<GhostFrame>, defaultTint: Int ->
                        for (ghost in ghosts) {
                            val factor = (1f - (ghost.distance - 1) * 0.25f).coerceAtLeast(0.2f)
                            val alpha = (onion.settings.opacity * factor).coerceIn(0.01f, 1f)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                            if (defaultTint != 0) {
                                paint.colorFilter = PorterDuffColorFilter(defaultTint, PorterDuff.Mode.SRC_IN)
                            }
                            for (layer in ghost.layers) {
                                if (!layer.visible) continue
                                paint.alpha = (layer.opacity * alpha * 255f).toInt().coerceIn(0, 255)
                                nativeCanvas.drawBitmap(layer.bitmap, null,
                                    RectF(ox, oy, ox + dw, oy + dh), paint)
                            }
                        }
                    }
                    drawGhostGroup(onion.before, onion.settings.tintBefore)
                    drawGhostGroup(onion.after, onion.settings.tintAfter)
                }

                // Current frame layers
                val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG)
                for (layer in layers()) {
                    if (!layer.visible) continue
                    layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                    nativeCanvas.drawBitmap(layer.bitmap, null,
                        RectF(ox, oy, ox + dw, oy + dh), layerPaint)
                }

                // Active Lasso Selection floating overlay
                val sel = activeLassoSelection()
                if (sel != null && !sel.pixels.isRecycled) {
                    val matrix = Matrix(sel.getMatrix())
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(ox, oy)

                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    nativeCanvas.drawBitmap(sel.pixels, matrix, paint)

                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = android.graphics.Color.BLUE
                        strokeWidth = 3f
                        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    }
                    val bounds = sel.bounds
                    val targetLeft = bounds.left + sel.translateX
                    val targetTop = bounds.top + sel.translateY
                    val w = bounds.width() * sel.scaleX
                    val h = bounds.height() * sel.scaleY

                    val viewRect = RectF(
                        ox + targetLeft * scale,
                        oy + targetTop * scale,
                        ox + (targetLeft + w) * scale,
                        oy + (targetTop + h) * scale
                    )
                    nativeCanvas.drawRect(viewRect, strokePaint)
                }

                nativeCanvas.restore()
            }
        }

        if (zoom != 1f || pan != Offset.Zero || rotation != 0f || isFlippedHorizontally || isFlippedVertically) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                TextButton(onClick = {
                    zoom = 1f
                    pan = Offset.Zero
                    rotation = 0f
                    isFlippedHorizontally = false
                    isFlippedVertically = false
                }) {
                    Text("Reset Canvas View", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
