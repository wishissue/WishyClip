package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.CloudTokens

/** The big filled circle for the one most important action in a bar (play / pause). */
@Composable
fun PrimaryRoundButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = WishyTheme.tokens.primaryButtonSize,
    enabled: Boolean = true
) {
    val tokens = WishyTheme.tokens
    Box(
        modifier = modifier
            .size(size)
            .bouncyClickable(enabled = enabled, pressedScale = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .background(tokens.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.5f),
            tint = tokens.onPrimary
        )
    }
}

@Preview(name = "PrimaryRoundButton")
@Composable
private fun PrimaryRoundButtonPreview() {
    WishyTheme(tokens = CloudTokens) {
        PrimaryRoundButton(iconRes = WishyIcons.Play, contentDescription = "Play", onClick = {})
    }
}
