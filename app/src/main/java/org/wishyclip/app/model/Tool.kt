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
    EYEDROPPER
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

val Tool.isBrush: Boolean get() = this in BRUSH_TOOLS

val Tool.displayName: String
    get() = when (this) {
        Tool.PIXEL -> "Pixel Pen"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }
