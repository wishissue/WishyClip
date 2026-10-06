package org.wishyclip.app.model

/** Drawing tools that are implemented. */
enum class Tool {
    PEN,
    PENCIL,
    MARKER,
    ERASER,
    AIRBRUSH,
    CALLIGRAPHY,
    HIGHLIGHTER,
    FILL,
    LASSO,
    LINE,
    RECT,
    ELLIPSE,
    TEXT,
    CHARCOAL,
    INK,
    WATERCOLOR,
    CHALK,
    PIXEL,
    EYEDROPPER,
    /** An imported tip brush (see brush/ package). Which one is held by the editor. */
    CUSTOM
}

/** Tools that live inside the Brush menu (one rail button opens a picker, like FlipaClip). */
val BRUSH_TOOLS: List<Tool> = listOf(
    Tool.PEN,
    Tool.PENCIL,
    Tool.MARKER,
    Tool.AIRBRUSH,
    Tool.CALLIGRAPHY,
    Tool.HIGHLIGHTER,
    Tool.CHARCOAL,
    Tool.INK,
    Tool.WATERCOLOR,
    Tool.CHALK,
    Tool.PIXEL
)

val Tool.isBrush: Boolean get() = this in BRUSH_TOOLS || this == Tool.CUSTOM

val Tool.displayName: String
    get() = when (this) {
        Tool.PIXEL -> "Pixel Pen"
        Tool.CUSTOM -> "Custom brush"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }
