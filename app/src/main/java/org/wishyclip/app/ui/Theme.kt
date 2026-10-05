package org.wishyclip.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFE0457F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E6),
    onPrimaryContainer = Color(0xFF3B0A1F),
    background = Color(0xFFFFF8FA),
    surface = Color(0xFFFFF8FA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8FB8),
    onPrimary = Color(0xFF4A0F2B),
    primaryContainer = Color(0xFF5A2A3F),
    onPrimaryContainer = Color(0xFFFFD9E6)
)

@Composable
fun WishyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
