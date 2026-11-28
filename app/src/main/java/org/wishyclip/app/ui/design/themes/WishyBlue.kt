package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import org.wishyclip.app.ui.design.WishyTokens

/**
 * "Wishy Blue": the brand palette created by WishIssue (https://github.com/wishissue), built around
 * the signature logo blue #0166FF. Mapped here onto Wishy Clip's token model in four variants:
 * light, dark, dusk (a softer blue-navy dark) and OLED (true black).
 *
 * The drawing paper stays white in every variant; the canvas backdrop is the stage around it.
 */
val WishyBlueLightTokens = WishyTokens(
    name = "Wishy Blue",
    isDark = false,
    primary = Color(0xFF0166FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF4B5B73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE7F8),
    onSecondaryContainer = Color(0xFF14213A),
    tertiary = Color(0xFF946200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF301F00),
    surface = Color(0xFFE6F1FF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFDAE5F6),
    onSurfaceVariant = Color(0xFF536176),
    outline = Color(0xFF7A879B),
    outlineVariant = Color(0xFFD4DEED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF3F7FF),
    surfaceContainerHighest = Color(0xFFE9F0FC),
    inverseSurface = Color(0xFF101828),
    inverseOnSurface = Color(0xFFF1F4FB),
    inversePrimary = Color(0xFF66A3FF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    danger = Color(0xFFBA1A1A),
    canvasBackdrop = Color(0xFFD4DEED),
    toolRail = Color(0xFFFFFFFF),
    timeline = Color(0xFFF8FBFF),
    accent = Color(0xFF0166FF),
    onionPreviousTint = Color(0xFFE5484D),
    onionNextTint = Color(0xFF237A4B),
    paper = Color(0xFFFFFFFF)
)

/** Wishy Blue on the dark tone mapping: near-black navy surfaces, soft blue accents. */
val WishyBlueDarkTokens = WishyTokens(
    name = "Wishy Blue Dark",
    isDark = true,
    primary = Color(0xFF66A3FF),
    onPrimary = Color(0xFF002266),
    primaryContainer = Color(0xFF0044B3),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB4C6E4),
    onSecondary = Color(0xFF1F3050),
    secondaryContainer = Color(0xFF2A3B5C),
    onSecondaryContainer = Color(0xFFD7E3FA),
    tertiary = Color(0xFFF4C66A),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDEA8),
    surface = Color(0xFF080B12),
    onSurface = Color(0xFFF1F4FB),
    surfaceVariant = Color(0xFF182235),
    onSurfaceVariant = Color(0xFFB0BCD0),
    outline = Color(0xFF6F7C93),
    outlineVariant = Color(0xFF303B4F),
    surfaceContainerLowest = Color(0xFF05070C),
    surfaceContainerLow = Color(0xFF0D1220),
    surfaceContainer = Color(0xFF111725),
    surfaceContainerHigh = Color(0xFF182235),
    surfaceContainerHighest = Color(0xFF202C42),
    inverseSurface = Color(0xFFF1F4FB),
    inverseOnSurface = Color(0xFF101828),
    inversePrimary = Color(0xFF0166FF),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    danger = Color(0xFFFFB4AB),
    canvasBackdrop = Color(0xFF05070C),
    toolRail = Color(0xFF111725),
    timeline = Color(0xFF0D1220),
    accent = Color(0xFF66A3FF),
    onionPreviousTint = Color(0xFFFF8A8F),
    onionNextTint = Color(0xFF72D6A0),
    paper = Color(0xFFFFFFFF)
)

/** Wishy Blue Dusk: the dark palette on softer blue-navy surfaces (easier on the eyes than near-black). */
val WishyBlueDuskTokens = WishyBlueDarkTokens.copy(
    name = "Wishy Blue Dusk",
    surface = Color(0xFF0F1626),
    surfaceVariant = Color(0xFF283553),
    surfaceContainerLowest = Color(0xFF0A101D),
    surfaceContainerLow = Color(0xFF131B2D),
    surfaceContainer = Color(0xFF18223A),
    surfaceContainerHigh = Color(0xFF1F2B45),
    surfaceContainerHighest = Color(0xFF283553),
    canvasBackdrop = Color(0xFF0A101D),
    toolRail = Color(0xFF18223A),
    timeline = Color(0xFF131B2D)
)

/** Wishy Blue OLED: true-black background for AMOLED screens, with the same blue accents. */
val WishyBlueOledTokens = WishyBlueDarkTokens.copy(
    name = "Wishy Blue OLED",
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1C2538),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080B12),
    surfaceContainer = Color(0xFF0E1320),
    surfaceContainerHigh = Color(0xFF151C2D),
    surfaceContainerHighest = Color(0xFF1C2538),
    canvasBackdrop = Color(0xFF000000),
    toolRail = Color(0xFF0E1320),
    timeline = Color(0xFF080B12)
)
