package org.wishyclip.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/** Mirrors the "Haptic feedback" setting; provided once at the app root. */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * Standard Material 3 click handling (ripple, focus and a11y role) plus an optional light haptic
 * tick. The name and the [pressedScale] parameter are kept so existing call sites compile; the
 * squish animation is gone because Material 3 communicates presses with the ripple.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    this.clickable(
        enabled = enabled,
        role = Role.Button,
        onClick = {
            if (haptic && hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}
