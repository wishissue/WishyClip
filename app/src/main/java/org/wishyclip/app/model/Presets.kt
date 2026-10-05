package org.wishyclip.app.model

data class CanvasPreset(val label: String, val width: Int, val height: Int)

object Presets {
    val canvasSizes: List<CanvasPreset> = listOf(
        CanvasPreset("HD 1280x720", 1280, 720),
        CanvasPreset("Full HD 1920x1080", 1920, 1080),
        CanvasPreset("Square 1080x1080", 1080, 1080),
        CanvasPreset("Portrait 720x1280", 720, 1280)
    )
    const val DEFAULT_FPS: Int = 12
}
