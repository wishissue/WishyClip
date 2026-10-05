package org.wishyclip.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.input.motionprediction.MotionEventPredictor
import org.wishyclip.app.model.Tool

class WishyDrawingSurface(
    context: Context,
    private val projectWidth: Int,
    private val projectHeight: Int,
    private val getLayers: () -> List<LayerData>,
    private val getTool: () -> Tool,
    private val getColor: () -> Int,
    private val getBrushSize: () -> Float,
    private val getOpacity: () -> Float,
    private val getStabilizer: () -> Float,
    private val onStrokeEnd: (Bitmap) -> Unit
) : SurfaceView(context), SurfaceHolder.Callback {

    private val predictor = MotionEventPredictor.newInstance(this)

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event)
        }
        predictor.record(event)
        val predicted = predictor.predict()

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && event.pressure < 0.05f) {
                return false
            }
        }

        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.lockHardwareCanvas()
        } else {
            holder.lockCanvas()
        }

        if (canvas != null) {
            try {
                canvas.drawColor(Color.WHITE)
                for (layer in getLayers()) {
                    if (layer.visible) {
                        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                            alpha = (layer.opacity * 255f).toInt()
                        }
                        canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        if (predicted != null) {
            predicted.recycle()
        }

        return true
    }
}

@Composable
fun DrawingSurfaceViewContainer(
    projectWidth: Int,
    projectHeight: Int,
    layers: () -> List<LayerData>,
    tool: () -> Tool,
    color: () -> Int,
    brushSize: () -> Float,
    opacity: () -> Float,
    stabilizer: () -> Float,
    onStrokeEnd: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WishyDrawingSurface(
                context = context,
                projectWidth = projectWidth,
                projectHeight = projectHeight,
                getLayers = layers,
                getTool = tool,
                getColor = color,
                getBrushSize = brushSize,
                getOpacity = opacity,
                getStabilizer = stabilizer,
                onStrokeEnd = onStrokeEnd
            )
        },
        modifier = modifier
    )
}
