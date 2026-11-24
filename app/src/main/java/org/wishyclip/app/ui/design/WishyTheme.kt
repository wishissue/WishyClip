package org.wishyclip.app.ui.design

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.themes.CoralDarkTokens
import org.wishyclip.app.ui.design.themes.CoralLightTokens
import org.wishyclip.app.ui.design.themes.ThemeImporter

val LocalWishyTokens = staticCompositionLocalOf { org.wishyclip.app.ui.design.themes.WishyLightTokens }

object WishyTheme {
    val tokens: WishyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalWishyTokens.current
}

/** The Material 3 shape scale (extra small 4, small 8, medium 12, large 16, extra large 28 dp). */
val WishyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Applies [tokens] to the subtree as a complete Material 3 theme (color scheme, shapes, typography).
 * When called without arguments it INHERITS the tokens of the enclosing theme.
 *
 * A theme with [WishyTokens.dynamic] set takes its colors from the wallpaper on Android 12+
 * (Material You) and falls back to the Coral palette on older versions.
 */
@Composable
fun WishyTheme(
    tokens: WishyTokens = LocalWishyTokens.current,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolved = remember(tokens, context) {
        if (tokens.dynamic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scheme = if (tokens.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                tokens.fromColorScheme(scheme)
            } else {
                (if (tokens.isDark) CoralDarkTokens else CoralLightTokens).copy(name = tokens.name, dynamic = true)
            }
        } else tokens
    }
    val colorScheme = remember(resolved) { resolved.toColorScheme() }
    // What components read through WishyTheme.tokens: the same palette as the color scheme, with the
    // Material 3 roles filled in and the Material 3 corner scale.
    val effective = remember(resolved, colorScheme) {
        resolved.copy(
            tertiary = colorScheme.tertiary,
            onTertiary = colorScheme.onTertiary,
            tertiaryContainer = colorScheme.tertiaryContainer,
            onTertiaryContainer = colorScheme.onTertiaryContainer,
            outline = colorScheme.outline,
            outlineVariant = colorScheme.outlineVariant,
            surfaceContainerLowest = colorScheme.surfaceContainerLowest,
            surfaceContainerLow = colorScheme.surfaceContainerLow,
            surfaceContainer = colorScheme.surfaceContainer,
            surfaceContainerHigh = colorScheme.surfaceContainerHigh,
            surfaceContainerHighest = colorScheme.surfaceContainerHighest,
            inverseSurface = colorScheme.inverseSurface,
            inverseOnSurface = colorScheme.inverseOnSurface,
            inversePrimary = colorScheme.inversePrimary,
            errorContainer = colorScheme.errorContainer,
            onErrorContainer = colorScheme.onErrorContainer,
            smallRadius = 8.dp,
            mediumRadius = 12.dp,
            largeRadius = 16.dp
        )
    }

    CompositionLocalProvider(
        LocalWishyTokens provides effective
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = WishyShapes,
            typography = WishyTypography,
            content = content
        )
    }
}

private fun Color.orElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

/** Builds the full Material 3 [ColorScheme]; roles a theme does not define are derived from the ones it does. */
internal fun WishyTokens.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    val onSurfaceMix = if (isDark) Color.White else Color.Black
    val tertiaryColor = tertiary.orElse(accent)
    val tertiaryContainerColor = tertiaryContainer.orElse(lerp(surface, tertiaryColor, if (isDark) 0.40f else 0.22f))
    val errorContainerColor = errorContainer.orElse(lerp(surface, danger, if (isDark) 0.40f else 0.20f))
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary.orElse(lerp(primary, surface, 0.5f)),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiaryColor,
        onTertiary = onTertiary.orElse(ThemeImporter.contrastOn(tertiaryColor)),
        tertiaryContainer = tertiaryContainerColor,
        onTertiaryContainer = onTertiaryContainer.orElse(ThemeImporter.contrastOn(tertiaryContainerColor)),
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface.orElse(onSurface),
        inverseOnSurface = inverseOnSurface.orElse(surface),
        error = danger,
        onError = ThemeImporter.contrastOn(danger),
        errorContainer = errorContainerColor,
        onErrorContainer = onErrorContainer.orElse(ThemeImporter.contrastOn(errorContainerColor)),
        outline = outline.orElse(lerp(onSurfaceVariant, surface, 0.35f)),
        outlineVariant = outlineVariant.orElse(lerp(surfaceVariant, onSurface, 0.12f)),
        scrim = Color.Black,
        surfaceBright = lerp(surface, Color.White, if (isDark) 0.08f else 0f),
        surfaceDim = lerp(surface, onSurfaceMix, if (isDark) 0f else 0.06f),
        surfaceContainerLowest = surfaceContainerLowest.orElse(lerp(surface, if (isDark) Color.Black else Color.White, 0.5f)),
        surfaceContainerLow = surfaceContainerLow.orElse(timeline),
        surfaceContainer = surfaceContainer.orElse(toolRail),
        surfaceContainerHigh = surfaceContainerHigh.orElse(lerp(toolRail, surfaceVariant, 0.5f)),
        surfaceContainerHighest = surfaceContainerHighest.orElse(surfaceVariant)
    )
}

/** Copies every color from a (dynamic) Material 3 scheme into the tokens, keeping drawing-specific values. */
private fun WishyTokens.fromColorScheme(s: ColorScheme): WishyTokens = copy(
    primary = s.primary,
    onPrimary = s.onPrimary,
    primaryContainer = s.primaryContainer,
    onPrimaryContainer = s.onPrimaryContainer,
    secondary = s.secondary,
    onSecondary = s.onSecondary,
    secondaryContainer = s.secondaryContainer,
    onSecondaryContainer = s.onSecondaryContainer,
    tertiary = s.tertiary,
    onTertiary = s.onTertiary,
    tertiaryContainer = s.tertiaryContainer,
    onTertiaryContainer = s.onTertiaryContainer,
    surface = s.surface,
    onSurface = s.onSurface,
    surfaceVariant = s.surfaceVariant,
    onSurfaceVariant = s.onSurfaceVariant,
    outline = s.outline,
    outlineVariant = s.outlineVariant,
    surfaceContainerLowest = s.surfaceContainerLowest,
    surfaceContainerLow = s.surfaceContainerLow,
    surfaceContainer = s.surfaceContainer,
    surfaceContainerHigh = s.surfaceContainerHigh,
    surfaceContainerHighest = s.surfaceContainerHighest,
    inverseSurface = s.inverseSurface,
    inverseOnSurface = s.inverseOnSurface,
    inversePrimary = s.inversePrimary,
    errorContainer = s.errorContainer,
    onErrorContainer = s.onErrorContainer,
    danger = s.error,
    accent = s.primary,
    canvasBackdrop = if (isDark) s.surfaceContainerLowest else s.surfaceContainerHigh,
    toolRail = s.surfaceContainer,
    timeline = s.surfaceContainerLow
)
