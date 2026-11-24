package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${clamped.roundToInt()}$unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = clamped,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished
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
