package org.wishyclip.app.canvas

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.wishyclip.app.model.LayerBlend

/** Maps [LayerBlend] to Porter-Duff transfer modes (all hardware-accelerated, API 26+ safe). */
object LayerBlending {
    private val cache = HashMap<LayerBlend, PorterDuffXfermode>()

    fun xfermode(blend: LayerBlend): PorterDuffXfermode? {
        val mode = when (blend) {
            LayerBlend.NORMAL -> return null
            LayerBlend.MULTIPLY -> PorterDuff.Mode.MULTIPLY
            LayerBlend.SCREEN -> PorterDuff.Mode.SCREEN
            LayerBlend.OVERLAY -> PorterDuff.Mode.OVERLAY
            LayerBlend.DARKEN -> PorterDuff.Mode.DARKEN
            LayerBlend.LIGHTEN -> PorterDuff.Mode.LIGHTEN
            LayerBlend.ADD -> PorterDuff.Mode.ADD
        }
        return cache.getOrPut(blend) { PorterDuffXfermode(mode) }
    }

    fun apply(paint: Paint, blend: LayerBlend) {
        paint.xfermode = xfermode(blend)
    }
}
