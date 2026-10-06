package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import java.util.Locale

private val PresetSwatches: List<Int> = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF5252.toInt(), 0xFFFF7A3D.toInt(),
    0xFFFFC107.toInt(), 0xFF4CAF50.toInt(), 0xFF00BCD4.toInt(), 0xFF2196F3.toInt(),
    0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt()
)

@Composable
fun ColorPickerSheet(
    initialColor: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val start = FloatArray(3)
    android.graphics.Color.colorToHSV(initialColor, start)
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    var hexText by remember(argb) {
        mutableStateOf(String.format(Locale.US, "#%06X", 0xFFFFFF and argb))
    }

    fun setFrom(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Color Picker", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                // Swatch comparison: Previous vs Current Color
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(tokens.mediumRadius))
                        .border(1.dp, tokens.surfaceVariant, RoundedCornerShape(tokens.mediumRadius))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(initialColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Prev", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(argb)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("New", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        try {
                            val clean = input.removePrefix("#").trim()
                            if (clean.length == 6) {
                                val parsed = clean.toLong(16).toInt() or 0xFF000000.toInt()
                                setFrom(parsed)
                            }
                        } catch (e: Exception) {}
                    },
                    label = { Text("Hex Code") },
                    modifier = Modifier.fillMaxWidth()
                )

                WishySlider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, label = "Hue", unit = "°")
                WishySlider(value = sat * 100f, onValueChange = { sat = it / 100f }, valueRange = 0f..100f, label = "Saturation", unit = "%")
                WishySlider(value = value * 100f, onValueChange = { value = it / 100f }, valueRange = 0f..100f, label = "Brightness", unit = "%")

                Text("Preset Swatches", style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (c in PresetSwatches.take(6)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.5.dp, if (argb == c) tokens.primary else tokens.surfaceVariant, CircleShape)
                                .clickable { setFrom(c) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (c in PresetSwatches.drop(6)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.5.dp, if (argb == c) tokens.primary else tokens.surfaceVariant, CircleShape)
                                .clickable { setFrom(c) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(name = "ColorPickerSheet Light")
@Composable
private fun ColorPickerSheetLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ColorPickerSheet(initialColor = 0xFFE53935.toInt(), onPick = {}, onDismiss = {})
    }
}

@Preview(name = "ColorPickerSheet Dark")
@Composable
private fun ColorPickerSheetDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ColorPickerSheet(initialColor = 0xFF1E88E5.toInt(), onPick = {}, onDismiss = {})
    }
}
