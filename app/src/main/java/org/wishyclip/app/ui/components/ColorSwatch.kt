package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/** Round color chip with an outer ring; the ring turns accent-colored when [selected]. */
@Composable
fun ColorSwatch(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 48.dp
) {
    val tokens = WishyTheme.tokens
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 2.dp,
                color = if (selected) tokens.primary else tokens.onSurfaceVariant,
                shape = CircleShape
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {}
}

@Preview(name = "ColorSwatch Light")
@Composable
private fun ColorSwatchLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ColorSwatch(color = Color.Red, selected = true, onClick = {})
    }
}

@Preview(name = "ColorSwatch Dark")
@Composable
private fun ColorSwatchDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ColorSwatch(color = Color.Cyan, selected = false, onClick = {})
    }
}
