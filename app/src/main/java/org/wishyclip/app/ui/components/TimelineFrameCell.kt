package org.wishyclip.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

private val ExposureStepWidth = 24.dp

/**
 * One frame in the timeline. Shows the drawing ([thumbnail] is anything Coil can load, null = blank),
 * a primary outline when selected, a number badge and, for held frames, a "x2" hold badge. A selected
 * cell has a grab handle on its right edge: drag it to hold the frame longer (FlipaClip's stretch).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineFrameCell(
    frameIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    exposureDuration: Int = 1,
    onIncreaseExposure: (() -> Unit)? = null,
    onDecreaseExposure: (() -> Unit)? = null,
    thumbnail: Any? = null,
    onLongClick: (() -> Unit)? = null,
    /** Called with the new hold length when the user drags the right-edge handle. */
    onExposureChange: ((Int) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = WishyTheme.tokens
    val shape = MaterialTheme.shapes.small
    val hold = exposureDuration.coerceAtLeast(1)
    val cellWidth = tokens.frameCellWidth + ExposureStepWidth * (hold - 1).coerceIn(0, 6)
    val stepPx = with(LocalDensity.current) { ExposureStepWidth.toPx() }
    var dragPx by remember { mutableFloatStateOf(0f) }
    val liveHold = (hold + (dragPx / stepPx).roundToInt()).coerceIn(1, 12)

    Box(
        modifier = modifier
            .size(width = cellWidth, height = tokens.frameCellHeight)
            .clip(shape)
            .background(tokens.paper)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(3.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (selected) scheme.primary else scheme.surfaceContainerHighest.copy(alpha = 0.85f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${frameIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimary else scheme.onSurface
            )
        }
        if (hold > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "x${if (dragPx != 0f) liveHold else hold}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer
                )
            }
        }
        if (selected && onExposureChange != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(14.dp)
                    .background(scheme.primary.copy(alpha = 0.18f))
                    .pointerInput(hold) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val target = (hold + (dragPx / stepPx).roundToInt()).coerceIn(1, 12)
                                dragPx = 0f
                                if (target != hold) onExposureChange(target)
                            },
                            onDragCancel = { dragPx = 0f },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                dragPx += dx
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .size(width = 3.dp, height = 20.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(scheme.primary)
                )
            }
        }
    }
}

@Preview(name = "TimelineFrameCell Selected")
@Composable
private fun TimelineFrameCellSelectedPreview() {
    WishyTheme(tokens = LightTokens) {
        TimelineFrameCell(frameIndex = 0, selected = true, onClick = {}, exposureDuration = 2, onExposureChange = {})
    }
}

@Preview(name = "TimelineFrameCell Unselected")
@Composable
private fun TimelineFrameCellUnselectedPreview() {
    WishyTheme(tokens = DarkTokens) {
        TimelineFrameCell(frameIndex = 2, selected = false, onClick = {})
    }
}
