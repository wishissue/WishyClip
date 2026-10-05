package org.wishyclip.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Swatches: List<Int> = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE53935.toInt(), 0xFFFB8C00.toInt(),
    0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(),
    0xFFFF6FA5.toInt(), 0xFF6D4C41.toInt()
)

/**
 * Simple HSV color picker built from Compose Sliders (no third-party dependency).
 * TODO(color): swap for skydoves ColorPickerView / compose-colorpicker (Apache 2.0) if a wheel is wanted.
 */
@Composable
fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = FloatArray(3)
    android.graphics.Color.colorToHSV(initial, start)
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    fun setFrom(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                Text("Hue")
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation")
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("Brightness")
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in Swatches.take(5)) SwatchDot(c) { setFrom(c) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in Swatches.drop(5)) SwatchDot(c) { setFrom(c) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SwatchDot(argb: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, Color.Gray, CircleShape)
            .clickable { onClick() }
    )
}
