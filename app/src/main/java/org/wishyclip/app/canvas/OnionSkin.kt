package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect

/**
 * Settings and renderer for onion skinning (ghosting previous/next animation frames).
 */
data class OnionSkinSettings(
    val enabled: Boolean = false,
    val framesBefore: Int = 1,
    val framesAfter: Int = 0,
    val opacity: Float = 0.35f,
    val tintBefore: Int = 0xFFFF4040.toInt(),
    val tintAfter: Int = 0xFF40C040.toInt()
)

data class GhostFrame(
    val distance: Int, // 1 for 1 frame away, 2 for 2 frames away, etc.
    val layers: List<LayerData>
)

data class OnionSkinData(
    val settings: OnionSkinSettings,
    val before: List<GhostFrame>,
    val after: List<GhostFrame>
)

interface OnionSkinRenderer {
    fun drawGhost(
        canvas: Canvas,
        ghost: GhostFrame,
        settings: OnionSkinSettings,
        tint: Int,
        dstRect: Rect
    )
}

object DefaultOnionSkinRenderer : OnionSkinRenderer {
    override fun drawGhost(
        canvas: Canvas,
        ghost: GhostFrame,
        settings: OnionSkinSettings,
        tint: Int,
        dstRect: Rect
    ) {
        val factor = (1f - (ghost.distance - 1) * 0.25f).coerceAtLeast(0.2f)
        val alpha = (settings.opacity * factor).coerceIn(0.01f, 1f)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (tint != 0) {
            paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
        for (layer in ghost.layers) {
            if (!layer.visible) continue
            paint.alpha = (layer.opacity * alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(layer.bitmap, null, dstRect, paint)
        }
    }
}

