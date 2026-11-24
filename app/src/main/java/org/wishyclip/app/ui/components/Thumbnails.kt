package org.wishyclip.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize

/** Transparency checkerboard (the universal "this area is empty / see-through" cue). */
fun Modifier.checkerboard(
    light: Color = Color(0xFFFFFFFF),
    dark: Color = Color(0xFFE0E0E0),
    cell: Dp = 6.dp
): Modifier = this.drawBehind {
    val c = cell.toPx()
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = row % 2
        while (x < size.width) {
            if (col % 2 == 1) {
                drawRect(
                    color = dark,
                    topLeft = Offset(x, y),
                    size = Size(minOf(c, size.width - x), minOf(c, size.height - y))
                )
            }
            x += c
            col++
        }
        y += c
        row++
    }
}

/**
 * A small preview of a layer: its pixels over a checkerboard in a rounded frame. [selected]
 * switches the frame to a 2dp primary outline. Empty layers show just the checkerboard.
 */
@Composable
fun LayerThumbnail(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false
) {
    val shape = MaterialTheme.shapes.small
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(shape)
            .checkerboard()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = shape
            )
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = if (dimmed) 0.4f else 1f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
