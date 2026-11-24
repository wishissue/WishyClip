package org.wishyclip.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The floating tonal surface of the editor (tool rail, property bar, timeline dock, popovers):
 * a Material 3 `surfaceContainer` container with the large (16dp) corner and level-2 elevation.
 * The name is kept for source compatibility; it is a plain M3 [Surface], not translucent "glass".
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        content = content
    )
}
