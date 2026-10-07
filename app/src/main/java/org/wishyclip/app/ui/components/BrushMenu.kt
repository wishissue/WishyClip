package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.sin
import org.wishyclip.app.brush.StoredBrush
import org.wishyclip.app.canvas.BrushPaints
import org.wishyclip.app.canvas.DabBrush
import org.wishyclip.app.canvas.DabPreview
import org.wishyclip.app.model.BRUSH_TOOLS
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.displayName
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens

/** Icon shown for a brush tool (rail button + menu rows). */
@DrawableRes
fun brushIcon(tool: Tool): Int = when (tool) {
    Tool.PENCIL -> WishyIcons.Pencil
    Tool.CHARCOAL -> WishyIcons.Charcoal
    Tool.MARKER -> WishyIcons.Marker
    Tool.CHALK -> WishyIcons.Chalk
    Tool.AIRBRUSH -> WishyIcons.Airbrush
    Tool.WATERCOLOR -> WishyIcons.Watercolor
    Tool.CALLIGRAPHY -> WishyIcons.Calligraphy
    Tool.INK -> WishyIcons.Ink
    Tool.PIXEL -> WishyIcons.Pixel
    Tool.HIGHLIGHTER -> WishyIcons.Highlighter
    Tool.CUSTOM -> WishyIcons.BrushCustom
    else -> WishyIcons.Pen
}

/**
 * FlipaClip-style Brush menu: its own panel (not mixed into the tool rail) with a list of brushes,
 * each showing a live stroke preview in the current color, and size / opacity / stabilizer sliders
 * for the selected brush at the bottom.
 */
@Composable
fun BrushMenu(
    selectedBrush: Tool,
    onSelectBrush: (Tool) -> Unit,
    color: Int,
    size: Float,
    onSizeChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    stabilizer: Float,
    onStabilizerChange: (Float) -> Unit,
    onSettingsFinished: () -> Unit,
    modifier: Modifier = Modifier,
    customBrushes: List<StoredBrush> = emptyList(),
    selectedCustomId: String? = null,
    onSelectCustom: (String) -> Unit = {},
    onDeleteCustom: (String) -> Unit = {},
    onImportBrushes: (() -> Unit)? = null,
    /** Builds the stamp used for the previews; null (missing tip) shows a "broken" hint. */
    dabFor: (StoredBrush) -> DabBrush? = { null },
    /** Live change of the selected custom brush's settings (kept in memory)... */
    onUpdateCustom: (StoredBrush) -> Unit = {},
    /** ...and saved to disk when the user lets go of a slider. */
    onCommitCustom: () -> Unit = {}
) {
    val tokens = WishyTheme.tokens
    GlassSurface(
        modifier = modifier.draggablePanel(),
        shape = RoundedCornerShape(tokens.largeRadius)
    ) {
        Column(
            // Scrolls as a whole so it stays usable in short (landscape) windows.
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(tokens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
        ) {
            Text(
                text = "Brushes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.onSurface
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
            ) {
                items(BRUSH_TOOLS) { brush ->
                    BrushRow(
                        tool = brush,
                        selected = brush == selectedBrush,
                        color = color,
                        size = size,
                        opacity = opacity,
                        onClick = { onSelectBrush(brush) }
                    )
                }
                items(customBrushes, key = { it.id }) { brush ->
                    CustomBrushRow(
                        brush = brush,
                        dab = dabFor(brush),
                        color = color,
                        selected = selectedBrush == Tool.CUSTOM && brush.id == selectedCustomId,
                        onClick = { onSelectCustom(brush.id) },
                        onDelete = { onDeleteCustom(brush.id) }
                    )
                }
            }
            if (onImportBrushes != null) {
                TextButton(onClick = onImportBrushes) {
                    Icon(
                        painter = painterResource(WishyIcons.Import),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("  Import brushes", style = MaterialTheme.typography.labelLarge)
                }
            }

            val selectedCustom = if (selectedBrush == Tool.CUSTOM) {
                customBrushes.firstOrNull { it.id == selectedCustomId }
            } else null
            if (selectedCustom != null) {
                CustomBrushSettings(
                    brush = selectedCustom,
                    onChange = onUpdateCustom,
                    onFinished = onCommitCustom
                )
            }

            WishySlider(
                value = size,
                onValueChange = onSizeChange,
                // Stamp brushes need room: a 8-60 px dab hides the tip's shape entirely.
                valueRange = 1f..(if (selectedBrush == Tool.CUSTOM) 200f else 60f),
                label = "Size",
                unit = "px",
                onValueChangeFinished = onSettingsFinished
            )
            WishySlider(
                value = opacity * 100f,
                onValueChange = { onOpacityChange(it / 100f) },
                valueRange = 5f..100f,
                label = "Opacity",
                unit = "%",
                onValueChangeFinished = onSettingsFinished
            )
            WishySlider(
                value = stabilizer * 100f,
                onValueChange = { onStabilizerChange(it / 100f) },
                valueRange = 0f..100f,
                label = "Stabilizer",
                unit = "%"
            )
        }
    }
}

@Composable
private fun CustomBrushRow(
    brush: StoredBrush,
    dab: DabBrush?,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val shape = RoundedCornerShape(tokens.smallRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tokens.primaryContainer else Color.Transparent)
            .then(
                if (selected) Modifier.border(BorderStroke(1.5.dp, tokens.primary), shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
    ) {
        Text(
            text = brush.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) tokens.onPrimaryContainer else tokens.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(84.dp)
        )
        if (dab != null) {
            // The same stamping rules as the canvas, drawn with the brush's own tip.
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                drawIntoCanvas {
                    DabPreview.draw(
                        canvas = it.nativeCanvas,
                        brush = dab,
                        argb = color,
                        width = this.size.width,
                        height = this.size.height,
                        diameter = this.size.height * 0.62f
                    )
                }
            }
        } else {
            Text(
                text = "Tip file missing - delete and re-import",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.danger,
                modifier = Modifier.weight(1f)
            )
        }
        ActionIconButton(
            iconRes = WishyIcons.Delete,
            contentDescription = "Delete brush ${brush.name}",
            onClick = onDelete
        )
    }
}

/** Shape settings of an imported brush: these decide whether a tip reads as a stamp or a smooth line. */
@Composable
private fun CustomBrushSettings(
    brush: StoredBrush,
    onChange: (StoredBrush) -> Unit,
    onFinished: () -> Unit
) {
    val tokens = WishyTheme.tokens
    Text(
        text = "Brush shape",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = tokens.onSurface
    )
    WishySlider(
        value = brush.spacing * 100f,
        onValueChange = { onChange(brush.copy(spacing = it / 100f)) },
        valueRange = 2f..200f,
        label = "Spacing",
        unit = "%",
        onValueChangeFinished = onFinished
    )
    WishySlider(
        value = brush.flow * 100f,
        onValueChange = { onChange(brush.copy(flow = it / 100f)) },
        valueRange = 5f..100f,
        label = "Flow",
        unit = "%",
        onValueChangeFinished = onFinished
    )
    WishySlider(
        value = brush.scatter * 100f,
        onValueChange = { onChange(brush.copy(scatter = it / 100f)) },
        valueRange = 0f..200f,
        label = "Scatter",
        unit = "%",
        onValueChangeFinished = onFinished
    )
    WishySlider(
        value = brush.sizeJitter * 100f,
        onValueChange = { onChange(brush.copy(sizeJitter = it / 100f)) },
        valueRange = 0f..100f,
        label = "Size jitter",
        unit = "%",
        onValueChangeFinished = onFinished
    )
    WishySlider(
        value = brush.angle,
        onValueChange = { onChange(brush.copy(angle = it)) },
        valueRange = 0f..360f,
        label = "Angle",
        unit = "°",
        onValueChangeFinished = onFinished
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Follow stroke direction",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = brush.rotateWithStroke,
            onCheckedChange = {
                onChange(brush.copy(rotateWithStroke = it))
                onFinished()
            }
        )
    }
}

@Composable
private fun BrushRow(
    tool: Tool,
    selected: Boolean,
    color: Int,
    size: Float,
    opacity: Float,
    onClick: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val shape = RoundedCornerShape(tokens.smallRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tokens.primaryContainer else Color.Transparent)
            .then(
                if (selected) Modifier.border(BorderStroke(1.5.dp, tokens.primary), shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
    ) {
        Icon(
            painter = painterResource(brushIcon(tool)),
            contentDescription = tool.displayName,
            modifier = Modifier.size(tokens.toolIconSize),
            tint = if (selected) tokens.primary else tokens.onSurfaceVariant
        )
        Text(
            text = tool.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) tokens.onPrimaryContainer else tokens.onSurface,
            modifier = Modifier.width(84.dp)
        )
        BrushStrokePreview(
            tool = tool,
            color = color,
            size = size,
            opacity = opacity,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(tokens.smallRadius))
                .background(tokens.paper)
        )
    }
}

/** Draws a wavy sample stroke with the real [BrushPaints] so the preview matches the canvas. */
@Composable
private fun BrushStrokePreview(
    tool: Tool,
    color: Int,
    size: Float,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val pad = 14f
        val midY = this.size.height / 2f
        val amp = this.size.height * 0.18f
        val width = this.size.width - pad * 2f
        // Keep the preview inside the swatch even for very large brush sizes.
        val previewSize = size.coerceIn(1f, this.size.height * 0.45f)
        val path = android.graphics.Path()
        val steps = 40
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = pad + width * t
            val y = midY + sin(t * 2f * Math.PI.toFloat()) * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val paint = BrushPaints.create(tool, color, previewSize, opacity)
        drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
    }
}

@Preview(name = "BrushMenu Dark")
@Composable
private fun BrushMenuPreview() {
    WishyTheme(tokens = DarkTokens) {
        Box(Modifier.padding(8.dp)) {
            BrushMenu(
                selectedBrush = Tool.PEN,
                onSelectBrush = {},
                color = 0xFF222222.toInt(),
                size = 12f,
                onSizeChange = {},
                opacity = 1f,
                onOpacityChange = {},
                stabilizer = 0.2f,
                onStabilizerChange = {},
                onSettingsFinished = {},
                modifier = Modifier.width(340.dp)
            )
        }
    }
}
