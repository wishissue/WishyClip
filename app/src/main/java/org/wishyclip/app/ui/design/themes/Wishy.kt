package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import org.wishyclip.app.ui.design.WishyTokens

/**
 * Wishy light theme: a complete Material 3 tonal palette generated from the WishIssue blue
 * (#1560D4, HCT hue 268). Key colors follow the standard role/tone mapping:
 * primary = T40, containers = T90, surfaces = N98 with surface containers N100..N90.
 */
val WishyLightTokens = WishyTokens(
    name = "Wishy",
    isDark = false,
    primary = Color(0xFF0058CB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF725572),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDD7FA),
    onTertiaryContainer = Color(0xFF2A132C),
    surface = Color(0xFFFBF8FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF1),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    surfaceContainerHighest = Color(0xFFE3E2E6),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFFB0C6FF),
    canvasBackdrop = Color(0xFFE9E7EC),
    toolRail = Color(0xFFEFEDF1),
    timeline = Color(0xFFF5F3F7),
    accent = Color(0xFF0058CB),
    danger = Color(0xFFBA1A1A),
    onionPreviousTint = Color(0xFFE5484D),
    onionNextTint = Color(0xFF237A4B),
    paper = Color(0xFFFFFFFF)
)

/** Wishy dark theme: the same blue palette on the Material 3 dark tone mapping (surface N6). */
val WishyDarkTokens = WishyTokens(
    name = "Wishy Dark",
    isDark = true,
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF00429B),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE0BBDE),
    onTertiary = Color(0xFF412742),
    tertiaryContainer = Color(0xFF593D5A),
    onTertiaryContainer = Color(0xFFFDD7FA),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF292A2D),
    surfaceContainerHighest = Color(0xFF343438),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF0058CB),
    canvasBackdrop = Color(0xFF0D0E11),
    toolRail = Color(0xFF1F1F23),
    timeline = Color(0xFF1B1B1F),
    accent = Color(0xFFB0C6FF),
    danger = Color(0xFFFFB4AB),
    onionPreviousTint = Color(0xFFFF8A8F),
    onionNextTint = Color(0xFF72D6A0),
    paper = Color(0xFFFFFFFF)
)
