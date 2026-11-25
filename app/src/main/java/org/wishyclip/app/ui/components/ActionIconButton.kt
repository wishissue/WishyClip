package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * Material 3 icon button for the top bar, timeline and panels. A normal action is a standard
 * [IconButton]; a toggled-on action ([selected]) becomes a [FilledTonalIconButton], the M3
 * "selected" treatment. The touch target is the M3 48dp minimum.
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
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val click: () -> Unit = {
        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    val icon: @Composable () -> Unit = {
        Icon(
            painter = painterResource(if (selected) WishyIcons.selected(iconRes) else iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
    val scheme = MaterialTheme.colorScheme
    if (selected) {
        FilledTonalIconButton(
            onClick = click,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                contentColor = tint ?: scheme.onSecondaryContainer
            ),
            content = icon
        )
    } else {
        IconButton(
            onClick = click,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tint ?: scheme.onSurfaceVariant
            ),
            content = icon
        )
    }
}

@Preview(name = "ActionIconButton Light")
@Composable
private fun ActionIconButtonLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = stringResource(R.string.ui_undo), onClick = {})
    }
}

@Preview(name = "ActionIconButton Dark")
@Composable
private fun ActionIconButtonDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = stringResource(R.string.ui_undo), selected = true, onClick = {})
    }
}
