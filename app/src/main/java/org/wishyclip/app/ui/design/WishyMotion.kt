package org.wishyclip.app.ui.design

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion for the interface chrome (panels, sheets, the timeline, settings). Two kinds of spring, as
 * in Material 3 Expressive: [spatial] for size / position / shape (slightly under-damped feel but no
 * overshoot on layout), and [effects] for colour and alpha (critically damped, never overshoots).
 *
 * Nothing here is used by the drawing canvas: ink, pan and zoom are never animated.
 * When the system's animation scale is 0 ("Remove animations" / reduced motion) every spec is a [snap].
 */
object WishyMotion {
    fun <T> spatial(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun <T> effects(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}

/** True when the user turned system animations off (animator duration scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
