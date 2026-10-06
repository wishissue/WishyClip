package org.wishyclip.app.model

/** How a layer is composited onto the layers below it. Stored by [name] in the database. */
enum class LayerBlend(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    ADD("Add");

    fun next(): LayerBlend = values()[(ordinal + 1) % values().size]

    companion object {
        fun from(name: String?): LayerBlend = values().firstOrNull { it.name == name } ?: NORMAL
    }
}
