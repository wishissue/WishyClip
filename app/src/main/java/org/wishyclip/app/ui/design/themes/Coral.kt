package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import org.wishyclip.app.ui.design.WishyTokens

/**
 * Coral: the classic frame-by-frame animation look from the UI/UX blueprint (neutral surfaces,
 * one vivid coral accent) expressed as a full Material 3 tonal palette (HCT hue 10, chroma 80).
 */
val CoralLightTokens = WishyTokens(
    name = "Coral",
    isDark = false,
    primary = Color(0xFFB81748),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF400012),
    secondary = Color(0xFF805158),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DD),
    onSecondaryContainer = Color(0xFF321017),
    tertiary = Color(0xFF7F561F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2B1700),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3DDDF),
    onSurfaceVariant = Color(0xFF524344),
    outline = Color(0xFF847374),
    outlineVariant = Color(0xFFD7C1C3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFEF1F1),
    surfaceContainer = Color(0xFFF8EBEB),
    surfaceContainerHigh = Color(0xFFF2E5E5),
    surfaceContainerHighest = Color(0xFFECE0E0),
    inverseSurface = Color(0xFF362F2F),
    inverseOnSurface = Color(0xFFFBEEEE),
    inversePrimary = Color(0xFFFFB2BC),
    canvasBackdrop = Color(0xFFF2E5E5),
    toolRail = Color(0xFFF8EBEB),
    timeline = Color(0xFFFEF1F1),
    accent = Color(0xFFB81748),
    danger = Color(0xFFBA1A1A),
    onionPreviousTint = Color(0xFFE5484D),
    onionNextTint = Color(0xFF237A4B),
    paper = Color(0xFFFFFFFF)
)

val CoralDarkTokens = WishyTokens(
    name = "Coral Dark",
    isDark = true,
    primary = Color(0xFFFFB2BC),
    onPrimary = Color(0xFF670023),
    primaryContainer = Color(0xFF910034),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFF3B7BE),
    onSecondary = Color(0xFF4B252B),
    secondaryContainer = Color(0xFF653A41),
    onSecondaryContainer = Color(0xFFFFD9DD),
    tertiary = Color(0xFFF3BC7B),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF643F07),
    onTertiaryContainer = Color(0xFFFFDDB8),
    surface = Color(0xFF181212),
    onSurface = Color(0xFFECE0E0),
    surfaceVariant = Color(0xFF524344),
    onSurfaceVariant = Color(0xFFD7C1C3),
    outline = Color(0xFF9F8C8E),
    outlineVariant = Color(0xFF524344),
    surfaceContainerLowest = Color(0xFF120D0D),
    surfaceContainerLow = Color(0xFF201A1B),
    surfaceContainer = Color(0xFF241E1F),
    surfaceContainerHigh = Color(0xFF2F2829),
    surfaceContainerHighest = Color(0xFF3A3334),
    inverseSurface = Color(0xFFECE0E0),
    inverseOnSurface = Color(0xFF362F2F),
    inversePrimary = Color(0xFFB81748),
    canvasBackdrop = Color(0xFF120D0D),
    toolRail = Color(0xFF241E1F),
    timeline = Color(0xFF201A1B),
    accent = Color(0xFFFFB2BC),
    danger = Color(0xFFFFB4AB),
    onionPreviousTint = Color(0xFFFF8A8F),
    onionNextTint = Color(0xFF72D6A0),
    paper = Color(0xFFFFFFFF)
)

/**
 * Material You: on Android 12+ every color comes from the wallpaper (see WishyTheme); the values
 * below are only the fallback used on older Android versions (the Coral palette) and for the
 * theme chip's color dot.
 */
val MaterialYouTokens = CoralLightTokens.copy(name = "Material You", dynamic = true)
