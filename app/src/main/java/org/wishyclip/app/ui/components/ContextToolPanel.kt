package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wishyclip.app.canvas.LassoSelection
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.displayName
import org.wishyclip.app.model.isBrush
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme

/**
 * Contextual Control Bar displayed on canvas for the active tool.
 * Only displays relevant controls for the active tool (Brush, Eraser, Fill, Lasso, Shapes, Text).
 */
@Composable
fun ContextToolPanel(
    tool: Tool,
    color: Int,
    brushSize: Float,
    opacity: Float,
    fillTolerance: Int,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onOpenColorPicker: () -> Unit,
    onOpenBrushMenu: () -> Unit,
    activeLasso: LassoSelection?,
    onCommitLasso: () -> Unit,
    onCancelLasso: () -> Unit,
    onDeleteLasso: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectShapeTool: ((Tool) -> Unit)? = null
) {
    val tokens = WishyTheme.tokens

    GlassSurface(
        modifier = modifier.draggablePanel(),
        shape = RoundedCornerShape(tokens.largeRadius)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
        ) {
            when {
                activeLasso != null -> {
                    TextButton(onClick = onCommitLasso) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onCancelLasso) {
                        Text("Put back")
                    }
                    TextButton(onClick = onDeleteLasso) {
                        Text("Delete", color = tokens.danger)
                    }
                }

                tool.isBrush -> {
                    // Brush Selector Chip
                    Box(
                        modifier = Modifier
                            .heightIn(min = tokens.minTouchTarget)
                            .bouncyClickable(onClick = onOpenBrushMenu)
                            .clip(RoundedCornerShape(tokens.mediumRadius))
                            .background(tokens.surfaceVariant)
                            .padding(horizontal = tokens.spaceMedium, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(brushIcon(tool)),
                                contentDescription = "Select brush",
                                tint = tokens.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tool.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Size Quick Control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(130.dp)
                    ) {
                        Text(
                            text = "${brushSize.roundToInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onSurfaceVariant,
                            modifier = Modifier.width(32.dp)
                        )
                        WishySlider(
                            value = brushSize,
                            onValueChange = onSizeChange,
                            valueRange = 1f..100f,
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Opacity Quick Control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(120.dp)
                    ) {
                        Text(
                            text = "${(opacity * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        WishySlider(
                            value = opacity * 100f,
                            onValueChange = { onOpacityChange(it / 100f) },
                            valueRange = 5f..100f,
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Color Chip
                    ColorSwatch(
                        color = Color(color),
                        onClick = onOpenColorPicker,
                        size = 32.dp
                    )
                }

                tool == Tool.ERASER -> {
                    Text(
                        text = "Eraser",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            text = "${brushSize.roundToInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        WishySlider(
                            value = brushSize,
                            onValueChange = onSizeChange,
                            valueRange = 1f..120f,
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                tool == Tool.FILL -> {
                    Text(
                        text = "Fill Tolerance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(
                            text = "$fillTolerance",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        WishySlider(
                            value = fillTolerance.toFloat(),
                            onValueChange = { onToleranceChange(it.roundToInt()) },
                            valueRange = 0f..255f,
                            label = "",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ColorSwatch(
                        color = Color(color),
                        onClick = onOpenColorPicker,
                        size = 32.dp
                    )
                }

                tool == Tool.LINE || tool == Tool.RECT || tool == Tool.ELLIPSE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionIconButton(
                            iconRes = WishyIcons.ShapeLine,
                            contentDescription = "Line",
                            selected = tool == Tool.LINE,
                            onClick = { onSelectShapeTool?.invoke(Tool.LINE) }
                        )
                        ActionIconButton(
                            iconRes = WishyIcons.ShapeRect,
                            contentDescription = "Rectangle",
                            selected = tool == Tool.RECT,
                            onClick = { onSelectShapeTool?.invoke(Tool.RECT) }
                        )
                        ActionIconButton(
                            iconRes = WishyIcons.ShapeEllipse,
                            contentDescription = "Ellipse",
                            selected = tool == Tool.ELLIPSE,
                            onClick = { onSelectShapeTool?.invoke(Tool.ELLIPSE) }
                        )
                    }
                    ColorSwatch(
                        color = Color(color),
                        onClick = onOpenColorPicker,
                        size = 32.dp
                    )
                }

                else -> {
                    Text(
                        text = tool.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    ColorSwatch(
                        color = Color(color),
                        onClick = onOpenColorPicker,
                        size = 32.dp
                    )
                }
            }
        }
    }
}
