package org.wishyclip.app.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWishyTokens = staticCompositionLocalOf { WishyTokens() }

object WishyTheme {
    val tokens: WishyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalWishyTokens.current
}

/**
 * Applies [tokens] to the subtree. When called without arguments it INHERITS the tokens of the
 * enclosing theme (previously it reset to the default light tokens, which made the theme picker
 * have almost no effect because every screen wraps itself in `WishyTheme { }`).
 */
@Composable
fun WishyTheme(
    tokens: WishyTokens = LocalWishyTokens.current,
    content: @Composable () -> Unit
) {
    val colorScheme = if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            primaryContainer = tokens.primaryContainer,
            onPrimaryContainer = tokens.onPrimaryContainer,
            secondary = tokens.secondary,
            onSecondary = tokens.onSecondary,
            secondaryContainer = tokens.secondaryContainer,
            onSecondaryContainer = tokens.onSecondaryContainer,
            background = tokens.surface,
            onBackground = tokens.onSurface,
            surface = tokens.surface,
            onSurface = tokens.onSurface,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.onSurfaceVariant,
            surfaceContainer = tokens.toolRail,
            surfaceContainerHigh = tokens.toolRail,
            surfaceContainerHighest = tokens.surfaceVariant,
            outline = tokens.onSurfaceVariant,
            outlineVariant = tokens.surfaceVariant,
            error = tokens.danger
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            primaryContainer = tokens.primaryContainer,
            onPrimaryContainer = tokens.onPrimaryContainer,
            secondary = tokens.secondary,
            onSecondary = tokens.onSecondary,
            secondaryContainer = tokens.secondaryContainer,
            onSecondaryContainer = tokens.onSecondaryContainer,
            background = tokens.surface,
            onBackground = tokens.onSurface,
            surface = tokens.surface,
            onSurface = tokens.onSurface,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.onSurfaceVariant,
            error = tokens.danger
        )
    }

    CompositionLocalProvider(
        LocalWishyTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WishyTypography,
            content = content
        )
    }
}
