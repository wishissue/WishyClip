package org.wishyclip.app.ui.components

import org.wishyclip.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun WishySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    onValueChangeFinished: (() -> Unit)? = null
) {
    val tokens = WishyTheme.tokens
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = if (valueRange.endInclusive > valueRange.start) {
        (clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    } else 0f

    val onFinishedState by rememberUpdatedState(onValueChangeFinished)
    val onValueChangeState by rememberUpdatedState(onValueChange)
    // The thumb is inset by its radius so it is never cut off at either end of the track.
    val thumbRadius = 10.dp
    fun valueAt(x: Float, width: Float, padPx: Float): Float {
        val span = (width - 2f * padPx).coerceAtLeast(1f)
        val f = ((x - padPx) / span).coerceIn(0f, 1f)
        return valueRange.start + f * (valueRange.endInclusive - valueRange.start)
    }

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = tokens.spaceXs),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.primaryContainer)
                        .padding(horizontal = tokens.spaceSmall, vertical = 2.dp)
                ) {
                    Text(
                        text = "${clampedValue.roundToInt()}$unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp) // comfortable touch target (was 28dp)
                // TalkBack / switch access: expose this as an adjustable range with a name and value.
                .semantics(mergeDescendants = true) {
                    if (label.isNotEmpty()) contentDescription = label
                    stateDescription = "${clampedValue.roundToInt()}$unit"
                    progressBarRangeInfo = ProgressBarRangeInfo(clampedValue, valueRange)
                    setProgress { target ->
                        onValueChangeState(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        onFinishedState?.invoke()
                        true
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newValue = valueAt(offset.x, size.width.toFloat(), thumbRadius.toPx())
                        onValueChangeState(newValue)
                        onFinishedState?.invoke()
                    }
                }
                .pointerInput(valueRange) {
                    detectDragGestures(
                        onDragEnd = { onFinishedState?.invoke() },
                        onDragCancel = { onFinishedState?.invoke() }
                    ) { change, _ ->
                        change.consume()
                        val newValue = valueAt(change.position.x, size.width.toFloat(), thumbRadius.toPx())
                        onValueChangeState(newValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f
                val trackHeight = 4.dp.toPx()
                val radius = thumbRadius.toPx()
                val trackStart = radius
                val trackSpan = (w - 2f * radius).coerceAtLeast(1f)

                // Inactive track
                drawRoundRect(
                    color = tokens.surfaceVariant,
                    topLeft = Offset(trackStart, centerY - trackHeight / 2f),
                    size = Size(trackSpan, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Active track
                val activeWidth = fraction * trackSpan
                if (activeWidth > 0) {
                    drawRoundRect(
                        color = tokens.primary,
                        topLeft = Offset(trackStart, centerY - trackHeight / 2f),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                }

                // Thumb
                val thumbX = trackStart + fraction * trackSpan
                drawCircle(
                    color = tokens.primary,
                    radius = radius,
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = tokens.surface,
                    radius = radius - 2.dp.toPx(),
                    center = Offset(thumbX, centerY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Preview(name = "WishySlider Light")
@Composable
private fun WishySliderLightPreview() {
    WishyTheme(tokens = LightTokens) {
        WishySlider(value = 16f, onValueChange = {}, valueRange = 1f..60f, label = stringResource(R.string.ui_brush_size), unit = "px")
    }
}

@Preview(name = "WishySlider Dark")
@Composable
private fun WishySliderDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        WishySlider(value = 80f, onValueChange = {}, valueRange = 0f..100f, label = stringResource(R.string.ui_opacity), unit = "%")
    }
}
