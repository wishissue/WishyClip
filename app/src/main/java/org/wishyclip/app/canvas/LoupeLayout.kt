package org.wishyclip.app.canvas

/** Where the magnifier sits: the top corner farther from the pen, so the hand never covers it. */
object LoupeLayout {
    /** True when the loupe should be in the top-left corner, false for top-right. All values in view pixels. */
    fun useLeftCorner(penX: Float, penY: Float, side: Float, margin: Float): Boolean {
        val cx = margin + side / 2f
        val cy = margin + side / 2f
        val dx = penX - cx
        val dy = penY - cy
        val keepAway = side * 1.15f
        return dx * dx + dy * dy > keepAway * keepAway
    }
}
