package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
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
    Column(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth(),
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
                    .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs)
            ) {
                Text(
                    text = "${value.roundToInt()}$unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.onPrimaryContainer
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = tokens.primary,
                activeTrackColor = tokens.primary,
                inactiveTrackColor = tokens.surfaceVariant,
                activeTickColor = tokens.primary,
                inactiveTickColor = tokens.surfaceVariant
            )
        )
    }
}

@Preview(name = "WishySlider Light")
@Composable
private fun WishySliderLightPreview() {
    WishyTheme(tokens = LightTokens) {
        WishySlider(value = 16f, onValueChange = {}, valueRange = 1f..60f, label = "Brush Size", unit = "px")
    }
}

@Preview(name = "WishySlider Dark")
@Composable
private fun WishySliderDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        WishySlider(value = 80f, onValueChange = {}, valueRange = 0f..100f, label = "Opacity", unit = "%")
    }
}
