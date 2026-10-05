package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/** A single tool: flat icon, the selected tool gets an accent-colored icon on a soft pill. */
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
    val iconColor = when {
        !enabled -> tokens.onSurface.copy(alpha = 0.35f)
        selected -> tokens.primary
        else -> tokens.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(tokens.toolButtonSize)
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(if (selected) tokens.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(tokens.toolIconSize),
            tint = iconColor
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
