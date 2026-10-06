package org.wishyclip.app.canvas

import android.graphics.Bitmap

/**
 * A stamp-based brush. [tip] is an ALPHA_8 bitmap (255 = ink), tinted with the current colour when
 * drawn. Spacing is a fraction of the dab diameter; scatter is a fraction of the diameter too.
 */
class DabBrush(
    val tip: Bitmap,
    val spacing: Float,
    val angle: Float,
    val rotateWithStroke: Boolean,
    val scatter: Float,
    val sizeJitter: Float,
    val flow: Float
)
