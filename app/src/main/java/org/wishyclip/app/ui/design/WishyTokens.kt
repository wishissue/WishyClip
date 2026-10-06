package org.wishyclip.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single data class holding ALL visual design values for Wishy Clip.
 */
data class WishyTokens(
    val name: String = "Default",
    /** True for dark palettes: selects the dark Material color scheme and light system-bar icons. */
    val isDark: Boolean = false,
    // Colors
    val primary: Color = Color(0xFF6750A4),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val primaryContainer: Color = Color(0xFFEADDFF),
    val onPrimaryContainer: Color = Color(0xFF21005D),
    val secondary: Color = Color(0xFF625B71),
    val onSecondary: Color = Color(0xFFFFFFFF),
    val secondaryContainer: Color = Color(0xFFE8DEF8),
    val onSecondaryContainer: Color = Color(0xFF1D192B),
    val surface: Color = Color(0xFFFEF7FF),
    val onSurface: Color = Color(0xFF1D1B20),
    val surfaceVariant: Color = Color(0xFFE7E0EC),
    val onSurfaceVariant: Color = Color(0xFF49454F),
    val canvasBackdrop: Color = Color(0xFFD9D9D9),
    val toolRail: Color = Color(0xFFF3EDF7),
    val timeline: Color = Color(0xFFF3EDF7),
    val accent: Color = Color(0xFF7D5260),
    val danger: Color = Color(0xFFB3261E),
    val onionPreviousTint: Color = Color(0xFFFF4040),
    val onionNextTint: Color = Color(0xFF40C040),
    /** Color of the drawing paper / frame cells (what the animation is drawn on). */
    val paper: Color = Color(0xFFFFFFFF),

    /** Hairline used on floating "glass" panels so they separate from the canvas backdrop. */
    val glassBorder: Color = Color(0x1F000000),
    /** Opacity of floating panels (toolbars, timeline). Lower = more frosted/see-through. */
    val glassAlpha: Float = 0.94f,

    // Radii (soft, Apple-style squircle feel)
    val smallRadius: Dp = 12.dp,
    val mediumRadius: Dp = 20.dp,
    val largeRadius: Dp = 32.dp,

    // Spacing
    val spaceXs: Dp = 6.dp,
    val spaceSmall: Dp = 10.dp,
    val spaceMedium: Dp = 14.dp,
    val spaceLarge: Dp = 20.dp,
    val spaceXl: Dp = 28.dp,
    /** Gap between a floating panel and the screen edge / neighbouring panels. */
    val floatMargin: Dp = 10.dp,

    // Dimensions (big, easy-to-hit targets)
    val toolIconSize: Dp = 22.dp,
    val actionIconSize: Dp = 28.dp,
    val toolButtonSize: Dp = 44.dp,
    val minTouchTarget: Dp = 52.dp,
    /** Large primary action (play, new project): the biggest thing on screen. */
    val primaryButtonSize: Dp = 64.dp,
    val topBarHeight: Dp = 64.dp,
    val toolRailWidth: Dp = 56.dp,
    val timelineHeight: Dp = 120.dp,
    val frameCellWidth: Dp = 80.dp,
    val frameCellHeight: Dp = 68.dp,

    // Elevation
    val elevationSmall: Dp = 2.dp,
    val elevationMedium: Dp = 8.dp,

    // Animations (ms)
    val animFastMs: Int = 150,
    val animNormalMs: Int = 300
)
