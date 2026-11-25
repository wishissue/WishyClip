package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * One drawing tool in the rail. The selected tool sits in a solid `primary` container with an
 * `onPrimary` icon, so the active tool is unmistakable; the others are plain icons with a ripple.
 * A double tap ([onDoubleClick]) opens the tool's options without blocking a single tap.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val scheme = MaterialTheme.colorScheme
    val containerTarget = if (selected) scheme.primary else Color.Transparent
    val contentTarget = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        selected -> scheme.onPrimary
        else -> scheme.onSurfaceVariant
    }
    val container by animateColorAsState(containerTarget, label = "toolContainer")
    val content by animateColorAsState(contentTarget, label = "toolContent")
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val isSelected = selected
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .semantics { this.selected = isSelected }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onDoubleClick = onDoubleClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(if (selected) WishyIcons.selected(iconRes) else iconRes),
            contentDescription = description,
            modifier = Modifier.size(26.dp),
            tint = content
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
