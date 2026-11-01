package org.wishyclip.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme

/** Mirrors the "Haptic feedback" setting; provided once at the app root. */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * Click handler with the soft "squish and spring back" press feel used across the app, plus a
 * light haptic tick. Replaces the flat ripple so every button reacts the same way.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyPress"
    )
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val tokens = WishyTheme.tokens
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        // Keyboard / D-pad focus must be visible (the press animation alone is not enough).
        .then(
            if (focused && enabled) Modifier.border(2.dp, tokens.primary, RoundedCornerShape(tokens.smallRadius))
            else Modifier
        )
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = {
                if (haptic && hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
}
