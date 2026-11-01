package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTokens

/**
 * WishIssue light theme (see WishIssue UI Design Guidelines, section 4).
 * Pale blue app background, white working surfaces, WishIssue blue for key actions only.
 * [WishyTokens.surface] is the app background; [WishyTokens.toolRail]/[WishyTokens.timeline]
 * are the white working surfaces that sit on top of it.
 */
val WishyLightTokens = WishyTokens(
    name = "Wishy",
    isDark = false,
    primary = Color(0xFF1560D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0A2A66),
    secondary = Color(0xFF536176),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6F5),
    onSecondaryContainer = Color(0xFF1B2B45),
    surface = Color(0xFFE6F1FF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFF3F7FF),
    onSurfaceVariant = Color(0xFF536176),
    canvasBackdrop = Color(0xFFD0DCEE),
    toolRail = Color(0xFFFFFFFF),
    timeline = Color(0xFFFFFFFF),
    accent = Color(0xFF1560D4),
    danger = Color(0xFFBA1A1A),
    onionPreviousTint = Color(0xFFE5484D),
    onionNextTint = Color(0xFF237A4B),
    paper = Color(0xFFFFFFFF),
    glassBorder = Color(0xFFD4DEED),
    glassAlpha = 0.97f,
    // Calmer corners and a flatter shadow than the pill-like defaults (guide: moderate, consistent radii).
    smallRadius = 10.dp,
    mediumRadius = 16.dp,
    largeRadius = 24.dp,
    elevationMedium = 4.dp
)

/** WishIssue dark theme: near-black background with navy-toned surfaces and the light-blue primary. */
val WishyDarkTokens = WishyTokens(
    name = "Wishy Dark",
    isDark = true,
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF082C63),
    primaryContainer = Color(0xFF1B3F78),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB0BCD0),
    onSecondary = Color(0xFF1B2538),
    secondaryContainer = Color(0xFF243149),
    onSecondaryContainer = Color(0xFFDCE6F5),
    surface = Color(0xFF080B12),
    onSurface = Color(0xFFF1F4FB),
    surfaceVariant = Color(0xFF182235),
    onSurfaceVariant = Color(0xFFB0BCD0),
    canvasBackdrop = Color(0xFF0D121D),
    toolRail = Color(0xFF111725),
    timeline = Color(0xFF111725),
    accent = Color(0xFFA8C8FF),
    danger = Color(0xFFFFB4AB),
    onionPreviousTint = Color(0xFFFF8A8F),
    onionNextTint = Color(0xFF72D6A0),
    paper = Color(0xFFFFFFFF),
    glassBorder = Color(0xFF303B4F),
    glassAlpha = 0.95f,
    smallRadius = 10.dp,
    mediumRadius = 16.dp,
    largeRadius = 24.dp,
    elevationMedium = 4.dp
)
