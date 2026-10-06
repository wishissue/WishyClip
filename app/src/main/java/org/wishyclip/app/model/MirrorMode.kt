package org.wishyclip.app.model

/** Symmetry drawing: strokes are mirrored across the canvas centre line(s). */
enum class MirrorMode(val flipX: Boolean, val flipY: Boolean, val label: String) {
    OFF(false, false, "Mirror off"),
    LEFT_RIGHT(true, false, "Mirror left/right"),
    TOP_BOTTOM(false, true, "Mirror top/bottom"),
    FOUR_WAY(true, true, "Mirror 4-way");

    /** Cycles Off -> Left/Right -> Top/Bottom -> 4-way -> Off. */
    fun next(): MirrorMode = values()[(ordinal + 1) % values().size]
}
