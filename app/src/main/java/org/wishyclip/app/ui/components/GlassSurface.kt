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
 * The app's one working surface: a solid, softly rounded panel with a hairline outline and a very
 * light shadow. Used for the top bar, tool dock, timeline and pop-over panels so they separate from
 * the canvas backdrop without stacking borders, blur and heavy elevation. (The name is kept for
 * source compatibility; it no longer renders translucent "glass".)
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WishyTheme.tokens.mediumRadius),
    content: @Composable () -> Unit
) {
    val tokens = WishyTheme.tokens
    Surface(
        modifier = modifier,
        shape = shape,
        color = tokens.toolRail,
        shadowElevation = tokens.elevationSmall,
        border = BorderStroke(1.dp, tokens.glassBorder),
        content = content
    )
}
