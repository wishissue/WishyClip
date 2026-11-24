package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.CoralLightTokens

/** The one filled (high-emphasis) Material 3 icon button in a bar: play / pause. */
@Composable
fun PrimaryRoundButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    enabled: Boolean = true
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = CircleShape
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Preview(name = "PrimaryRoundButton")
@Composable
private fun PrimaryRoundButtonPreview() {
    WishyTheme(tokens = CoralLightTokens) {
        PrimaryRoundButton(iconRes = WishyIcons.Play, contentDescription = stringResource(R.string.ui_play), onClick = {})
    }
}
