package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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

/**
 * Big, soft icon button used in the top bar, timeline and panels. When [selected] it gets a pastel
 * pill behind the accent-colored icon. Presses squish slightly and give a light haptic tick.
 */
@Composable
fun ActionIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val iconColor = when {
        !enabled -> tokens.onSurface.copy(alpha = 0.3f)
        tint != null -> tint
        selected -> tokens.primary
        else -> tokens.onSurface
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = tokens.minTouchTarget, minHeight = tokens.minTouchTarget)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(if (selected) tokens.primaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(tokens.actionIconSize),
            tint = iconColor
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
        ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = "Undo", selected = true, onClick = {})
    }
}
