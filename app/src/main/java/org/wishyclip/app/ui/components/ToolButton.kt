package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * A single tool: a big rounded button. The selected tool fills with the accent color and the
 * icon turns white, so the active tool is obvious at a glance (and easy to hit with a thumb).
 */
@Composable
fun ToolButton(
    @DrawableRes iconRes: Int,
    description: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val target = when {
        !enabled -> tokens.onSurface.copy(alpha = 0.35f)
        selected -> tokens.onPrimary
        else -> tokens.onSurfaceVariant
    }
    val iconColor by animateColorAsState(target, label = "toolIcon")
    val bgColor by animateColorAsState(
        if (selected) tokens.primary else Color.Transparent,
        label = "toolBg"
    )
    Box(
        modifier = modifier
            .size(tokens.toolButtonSize)
            .then(
                if (onDoubleClick != null) Modifier.pointerInput(enabled) {
                    detectTapGestures(onTap = { if (enabled) onClick() }, onDoubleTap = { if (enabled) onDoubleClick() })
                } else Modifier.bouncyClickable(enabled = enabled, onClick = onClick)
            )
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(bgColor),
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
