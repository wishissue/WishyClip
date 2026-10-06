package org.wishyclip.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme

/**
 * A floating, rounded, softly shadowed panel with a hairline border. Used for the top bar, tool
 * dock, timeline and pop-over panels so they read as frosted cards hovering over the canvas.
 * (Translucent tint only; there is no live background blur, which keeps drawing fast.)
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WishyTheme.tokens.largeRadius),
    content: @Composable () -> Unit
) {
    val tokens = WishyTheme.tokens
    Surface(
        modifier = modifier,
        shape = shape,
        color = tokens.toolRail.copy(alpha = tokens.glassAlpha),
        shadowElevation = tokens.elevationMedium,
        border = BorderStroke(1.dp, tokens.glassBorder),
        content = content
    )
}
