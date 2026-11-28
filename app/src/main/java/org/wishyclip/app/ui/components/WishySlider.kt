package org.wishyclip.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * Material 3 [Slider] with a label and a live numeric readout ("18 px", "42%"). When [label] is
 * empty only the slider is drawn (used inline in compact bars).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = WishyTheme.tokens.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${clamped.roundToInt()}$unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = WishyTheme.tokens.primary
                )
            }
        }
        val tokens = WishyTheme.tokens
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val fraction = ((clamped - valueRange.start) / span).coerceIn(0f, 1f)
        val activeColor = tokens.primary
        val inactiveColor = tokens.onSurfaceVariant.copy(alpha = 0.22f)
        val ringColor = tokens.surface
        // One look for every slider in the app: thick rounded track filled with the theme colour,
        // and a round thumb with a surface-coloured ring so it reads on any background.
        Slider(
            value = clamped,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(
                    Modifier
                        .size(24.dp)
                        .shadow(3.dp, CircleShape)
                        .background(ringColor, CircleShape)
                        .padding(4.dp)
                        .background(activeColor, CircleShape)
                )
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(inactiveColor, size = size, cornerRadius = r)
                    val w = (size.width * fraction).coerceAtLeast(size.height)
                    drawRoundRect(activeColor, size = Size(w, size.height), cornerRadius = r)
                }
            }
        )
    }
}

@Preview(name = "WishySlider Light")
@Composable
private fun WishySliderLightPreview() {
    WishyTheme(tokens = LightTokens) {
        WishySlider(value = 18f, onValueChange = {}, valueRange = 1f..60f, label = "Size", unit = " px")
    }
}

@Preview(name = "WishySlider Dark")
@Composable
private fun WishySliderDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        WishySlider(value = 42f, onValueChange = {}, valueRange = 0f..100f, label = "Opacity", unit = "%")
    }
}
