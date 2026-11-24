package org.wishyclip.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme data for Wishy Clip. The colors here are the INPUT of the Material 3 color scheme that
 * [WishyTheme] builds: components read `MaterialTheme.colorScheme`, shapes and typography, and only
 * use these tokens for the things Material 3 has no role for (drawing paper, canvas backdrop,
 * onion-skin tints) and for the layout dimensions of the editor.
 *
 * The optional Material 3 roles default to [Color.Unspecified]; when a theme does not set them
 * (older themes and imported JSON themes) they are derived from the colors that are set.
 */
data class WishyTokens(
    val name: String = "Default",
    /** True for dark palettes: selects the dark Material color scheme and light system-bar icons. */
    val isDark: Boolean = false,
    /** True for the "Material You" theme: colors come from the device wallpaper (Android 12+). */
    val dynamic: Boolean = false,

    // Core colors
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
    /** The workspace around the canvas (the "stage" background). */
    val canvasBackdrop: Color = Color(0xFFD9D9D9),
    /** Floating tool surfaces (maps to `surfaceContainer`). */
    val toolRail: Color = Color(0xFFF3EDF7),
    /** The timeline dock (maps to `surfaceContainerLow`). */
    val timeline: Color = Color(0xFFF3EDF7),
    val accent: Color = Color(0xFF7D5260),
    val danger: Color = Color(0xFFB3261E),
    val onionPreviousTint: Color = Color(0xFFFF4040),
    val onionNextTint: Color = Color(0xFF40C040),
    /** Color of the drawing paper / frame cells (what the animation is drawn on). */
    val paper: Color = Color(0xFFFFFFFF),

    // Optional full Material 3 roles (derived when Unspecified)
    val tertiary: Color = Color.Unspecified,
    val onTertiary: Color = Color.Unspecified,
    val tertiaryContainer: Color = Color.Unspecified,
    val onTertiaryContainer: Color = Color.Unspecified,
    val outline: Color = Color.Unspecified,
    val outlineVariant: Color = Color.Unspecified,
    val surfaceContainerLowest: Color = Color.Unspecified,
    val surfaceContainerLow: Color = Color.Unspecified,
    val surfaceContainer: Color = Color.Unspecified,
    val surfaceContainerHigh: Color = Color.Unspecified,
    val surfaceContainerHighest: Color = Color.Unspecified,
    val inverseSurface: Color = Color.Unspecified,
    val inverseOnSurface: Color = Color.Unspecified,
    val inversePrimary: Color = Color.Unspecified,
    val errorContainer: Color = Color.Unspecified,
    val onErrorContainer: Color = Color.Unspecified,

    /** Kept for source compatibility with saved themes; Material 3 outlines are used instead. */
    val glassBorder: Color = Color(0x1F000000),
    val glassAlpha: Float = 0.94f,

    // Shape scale: the Material 3 corner scale (8 / 12 / 16 dp). WishyTheme normalises every theme to it.
    val smallRadius: Dp = 8.dp,
    val mediumRadius: Dp = 12.dp,
    val largeRadius: Dp = 16.dp,

    // Spacing (Material 3 uses a 4dp grid)
    val spaceXs: Dp = 4.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 12.dp,
    val spaceLarge: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    /** Gap between a floating panel and the screen edge / neighbouring panels. */
    val floatMargin: Dp = 8.dp,

    // Dimensions
    val toolIconSize: Dp = 24.dp,
    val actionIconSize: Dp = 24.dp,
    val toolButtonSize: Dp = 48.dp,
    /** Material 3 minimum interactive size. */
    val minTouchTarget: Dp = 48.dp,
    /** Large primary action (play, new project). */
    val primaryButtonSize: Dp = 56.dp,
    val topBarHeight: Dp = 56.dp,
    val toolRailWidth: Dp = 56.dp,
    val timelineHeight: Dp = 120.dp,
    val frameCellWidth: Dp = 64.dp,
    val frameCellHeight: Dp = 56.dp,

    // Elevation (Material 3 levels 1..3)
    val elevationSmall: Dp = 1.dp,
    val elevationMedium: Dp = 3.dp,

    // Animations (ms), Material 3 "short" / "medium" durations
    val animFastMs: Int = 150,
    val animNormalMs: Int = 300
)
