package org.wishyclip.app.ui.components

import org.wishyclip.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import org.wishyclip.app.ui.design.WishyIcons
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    /** Closes the sheet and lets the user tap the canvas to pick a color; null hides the button. */
    onEyedropper: (() -> Unit)? = null,
    /**
     * The color that was active before the picker was first opened, shown as "Old" and restored by
     * Cancel. [initialColor] follows the brush live (every change is applied at once), so it cannot
     * be used for this; when null the color at first composition is used.
     */
    oldColor: Int? = null
) {
    val tokens = WishyTheme.tokens
    // Every change is applied to the brush immediately via onPick; this is what Cancel restores.
    val originalColor = remember { oldColor ?: initialColor }
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }

    val hsvArgb = remember(hue, sat, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    }
    // A color typed as hex (or tapped as a swatch) is kept EXACTLY; the HSV round trip can be off by one
    // in a channel. The exact value is used only while hue/saturation/value are still the ones it produced.
    var exactColor by remember { mutableStateOf<Int?>(null) }
    var exactHsv by remember { mutableStateOf<FloatArray?>(null) }
    val argb = exactColor?.takeIf {
        exactHsv?.let { h -> h[0] == hue && h[1] == sat && h[2] == value } == true
    } ?: hsvArgb

    // Selecting a color applies it right away (no separate confirm step).
    // (Skipped until something actually changed, so opening the sheet never nudges the color.)
    androidx.compose.runtime.LaunchedEffect(argb) {
        val changed = exactColor != null || hue != start[0] || sat != start[1] || value != start[2]
        if (changed) onPick(argb)
    }

    var hexText by remember(argb) {
        mutableStateOf(String.format(Locale.US, "%06X", 0xFFFFFF and argb))
    }

    fun setFrom(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        exactColor = c or 0xFF000000.toInt()
        exactHsv = floatArrayOf(hsv[0], hsv[1], hsv[2])
    }

    // Full-screen scrim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = modifier
                .padding(tokens.spaceMedium)
                .widthIn(max = 400.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(tokens.largeRadius)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(tokens.spaceMedium)
                    .heightIn(max = 480.dp)
            ) {
                val isWide = maxWidth > 320.dp && maxHeight < 400.dp

                Column(
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ui_color),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onEyedropper != null) {
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ui_pick_from_canvas)) { onEyedropper() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(WishyIcons.Eyedropper),
                                        contentDescription = stringResource(R.string.ui_pick_from_canvas),
                                        tint = tokens.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                                    .clickable(role = Role.Button) {
                                        onPick(originalColor)
                                        onDismiss()
                                    }
                                    .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceXs)
                            ) {
                                Text(stringResource(R.string.ui_cancel), style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                                    .background(tokens.primary)
                                    .clickable(role = Role.Button) { onDismiss() }
                                    .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs)
                            ) {
                                Text(stringResource(R.string.ui_done), style = MaterialTheme.typography.labelMedium, color = tokens.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spaceMedium),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SatValSquare(
                                hue = hue,
                                sat = sat,
                                value = value,
                                onSatValChange = { s, v ->
                                    sat = s
                                    value = v
                                },
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                            ) {
                                HueBar(
                                    hue = hue,
                                    onHueChange = { hue = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(tokens.smallRadius))
                                )
                                ColorComparisonAndHex(
                                    initialColor = originalColor,
                                    currentColor = argb,
                                    hexText = hexText,
                                    onHexChange = { input ->
                                        hexText = input
                                        try {
                                            val clean = input.removePrefix("#").trim()
                                            if (clean.length == 6) {
                                                val parsed = clean.toLong(16).toInt() or 0xFF000000.toInt()
                                                setFrom(parsed)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                )
                                PresetSwatchesGrid(
                                    currentColor = argb,
                                    onSelectSwatch = { setFrom(it); onPick(it or 0xFF000000.toInt()); onDismiss() }
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            SatValSquare(
                                hue = hue,
                                sat = sat,
                                value = value,
                                onSatValChange = { s, v ->
                                    sat = s
                                    value = v
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                            )

                            HueBar(
                                hue = hue,
                                onHueChange = { hue = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                            )

                            ColorComparisonAndHex(
                                initialColor = originalColor,
                                currentColor = argb,
                                hexText = hexText,
                                onHexChange = { input ->
                                    hexText = input
                                    try {
                                        val clean = input.removePrefix("#").trim()
                                        if (clean.length == 6) {
                                            val parsed = clean.toLong(16).toInt() or 0xFF000000.toInt()
                                            setFrom(parsed)
                                        }
                                    } catch (_: Exception) {}
                                }
                            )

                            PresetSwatchesGrid(
                                currentColor = argb,
                                onSelectSwatch = { setFrom(it); onPick(it or 0xFF000000.toInt()); onDismiss() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onSatValChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHueColor = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }

    Box(
        modifier = modifier
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onSatValChange(s, v)
                }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    onSatValChange(s, v)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(color = pureHueColor)

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )

            val handleX = sat * w
            val handleY = (1f - value) * h
            drawCircle(
                color = Color.Black,
                radius = 7.dp.toPx(),
                center = Offset(handleX, handleY),
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(handleX, handleY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green,
            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                    onHueChange(h)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val h = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                    onHueChange(h)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.horizontalGradient(colors = rainbowColors)
            )

            val handleX = (hue / 360f) * w
            drawCircle(
                color = Color.Black,
                radius = 8.dp.toPx(),
                center = Offset(handleX, h / 2f),
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(handleX, h / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun ColorComparisonAndHex(
    initialColor: Int,
    currentColor: Int,
    hexText: String,
    onHexChange: (String) -> Unit
) {
    val tokens = WishyTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(tokens.smallRadius))
                .border(1.dp, tokens.glassBorder, RoundedCornerShape(tokens.smallRadius))
        ) {
            ComparisonHalf(color = initialColor, label = stringResource(R.string.ui_color_old), modifier = Modifier.weight(1f))
            ComparisonHalf(color = currentColor, label = stringResource(R.string.ui_color_new), modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .width(100.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(tokens.smallRadius))
                .background(tokens.surfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", style = MaterialTheme.typography.labelSmall, color = tokens.onSurfaceVariant)
            BasicTextField(
                value = hexText,
                // Only hex digits (and an optional #), at most 7 characters: stops junk like "zz!!" or 40-char pastes.
                onValueChange = { raw ->
                    onHexChange(raw.filter { c -> c == '#' || c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(7))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(color = tokens.onSurface),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ComparisonHalf(color: Int, label: String, modifier: Modifier = Modifier) {
    val c = Color(color)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(c),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (c.luminance() > 0.5f) Color.Black else Color.White
        )
    }
}

@Composable
private fun PresetSwatchesGrid(
    currentColor: Int,
    onSelectSwatch: (Int) -> Unit
) {
    val tokens = WishyTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
        for (rowColors in listOf(PresetSwatches.take(6), PresetSwatches.drop(6))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (c in rowColors) {
                    val hex = "#%06X".format(c and 0xFFFFFF)
                    val swatchLabel = stringResource(R.string.ui_color_swatch, hex)
                    // Compare RGB only: the alpha byte is irrelevant to which swatch is "the" color.
                    val isSelected = (currentColor and 0xFFFFFF) == (c and 0xFFFFFF)
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = swatchLabel) { onSelectSwatch(c) }
                            .semantics { contentDescription = swatchLabel; selected = isSelected },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    if (isSelected) 3.dp else 1.5.dp,
                                    if (isSelected) tokens.primary else tokens.glassBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(WishyIcons.Check),
                                    contentDescription = null,
                                    tint = if (Color(c).luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
