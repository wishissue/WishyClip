package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun ToolButton(
    @DrawableRes iconRes: Int,
    description: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val scaleFactor by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = tween(tokens.animFastMs)
    )

    Box(
        modifier = modifier
            .size(tokens.toolButtonSize)
            .scale(scaleFactor)
            .padding(tokens.spaceXs)
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(if (selected) tokens.primaryContainer else tokens.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.surfaceVariant,
                shape = RoundedCornerShape(tokens.mediumRadius)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(tokens.toolIconSize),
            alpha = if (enabled) 1f else 0.35f
        )
    }
}

@Preview(name = "Light Mode")
@Composable
private fun ToolButtonLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ToolButton(iconRes = WishyIcons.Pen, description = "Pen", selected = true, onClick = {})
    }
}

@Preview(name = "Dark Mode")
@Composable
private fun ToolButtonDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ToolButton(iconRes = WishyIcons.Pen, description = "Pen", selected = false, onClick = {})
    }
}
