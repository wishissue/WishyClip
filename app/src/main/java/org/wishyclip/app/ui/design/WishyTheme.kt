package org.wishyclip.app.ui.design

import androidx.compose.material3.MaterialTheme
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

@Composable
fun WishyTheme(
    tokens: WishyTokens = WishyTokens(),
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = tokens.primary,
        onPrimary = tokens.onPrimary,
        primaryContainer = tokens.primaryContainer,
        onPrimaryContainer = tokens.onPrimaryContainer,
        secondary = tokens.secondary,
        onSecondary = tokens.onSecondary,
        secondaryContainer = tokens.secondaryContainer,
        onSecondaryContainer = tokens.onSecondaryContainer,
        surface = tokens.surface,
        onSurface = tokens.onSurface,
        surfaceVariant = tokens.surfaceVariant,
        onSurfaceVariant = tokens.onSurfaceVariant,
        error = tokens.danger
    )

    CompositionLocalProvider(
        LocalWishyTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
