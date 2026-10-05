package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun ActionIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tokens = WishyTheme.tokens
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = tokens.minTouchTarget, minHeight = tokens.minTouchTarget)
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(if (selected) tokens.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(tokens.spaceXs),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(tokens.actionIconSize),
            alpha = if (enabled) 1f else 0.35f
        )
    }
}

@Preview(name = "ActionIconButton Light")
@Composable
private fun ActionIconButtonLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = "Undo", onClick = {})
    }
}

@Preview(name = "ActionIconButton Dark")
@Composable
private fun ActionIconButtonDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = "Undo", onClick = {})
    }
}
