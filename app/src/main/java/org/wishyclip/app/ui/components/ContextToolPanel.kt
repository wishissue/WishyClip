package org.wishyclip.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.canvas.LassoSelection
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.displayName
import org.wishyclip.app.model.isBrush
import org.wishyclip.app.ui.design.WishyIcons

/**
 * Tool properties: a compact floating Material 3 bar that only shows what the ACTIVE tool needs
 * (brush: brush / size / opacity / color; eraser: size; fill: tolerance / color; selection: transform
 * actions ...). Chips open the shared options popover ([onOpenToolOptions]) or the brush menu, so
 * the bar stays one row and never covers the canvas with sliders. It scrolls sideways on narrow
 * screens instead of clipping.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onSelectShapeTool: ((Tool) -> Unit)? = null,
    /** Re-opens the text editor for the selected text (null hides the button). */
    onEditText: (() -> Unit)? = null,
    /** Opens the size / opacity popover for the active tool. */
    onOpenToolOptions: () -> Unit = {},
    /** Placement actions for an imported picture (only shown while one is being placed). */
    onFitLasso: () -> Unit = {},
    onFillLasso: () -> Unit = {},
    onRotateLasso: () -> Unit = {},
    onResetProportions: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    // Eraser and shapes adjust size right in the bar: the chip expands into a slider (no popover).
    var quickSize by remember(tool) { mutableStateOf(false) }

    @Composable
    fun SizeChip(label: String, inline: Boolean = false) {
        // The leading dot previews the real brush size (clamped so it always fits the chip).
        val dot = (brushSize / 2f).coerceIn(4f, 18f)
        AssistChip(
            onClick = if (inline) ({ quickSize = !quickSize }) else onOpenToolOptions,
            label = { Text(label) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(dot.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface)
                )
            }
        )
    }

    @Composable
    fun QuickSizeSlider() {
        if (quickSize) {
            WishySlider(
                value = brushSize,
                onValueChange = onSizeChange,
                valueRange = 1f..100f,
                label = "",
                modifier = Modifier.width(168.dp)
            )
        }
    }

    @Composable
    fun OpacityChip() {
        AssistChip(
            onClick = onOpenToolOptions,
            label = { Text("${(opacity * 100f).roundToInt()}%") }
        )
    }

    GlassSurface(modifier = modifier.draggablePanel()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                activeLasso != null && activeLasso.isImported -> {
                    Button(onClick = onCommitLasso) { Text(stringResource(R.string.ui_place)) }
                    ActionIconButton(
                        iconRes = WishyIcons.FitScreen,
                        contentDescription = stringResource(R.string.ui_fit_canvas),
                        onClick = onFitLasso
                    )
                    TextButton(onClick = onFillLasso) { Text(stringResource(R.string.ui_fill_canvas)) }
                    ActionIconButton(
                        iconRes = WishyIcons.RotateRight,
                        contentDescription = stringResource(R.string.ui_rotate_90),
                        onClick = onRotateLasso
                    )
                    TextButton(onClick = onResetProportions) { Text(stringResource(R.string.ui_reset_proportions)) }
                    TextButton(onClick = onCancelLasso) { Text(stringResource(R.string.ui_cancel)) }
                }

                activeLasso != null -> {
                    Button(onClick = onCommitLasso) { Text(stringResource(R.string.ui_done)) }
                    if (activeLasso.isText) {
                        if (onEditText != null) {
                            TextButton(onClick = onEditText) { Text(stringResource(R.string.ui_edit_text)) }
                        }
                        ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                        OpacityChip()
                    }
                    TextButton(onClick = onCancelLasso) { Text(stringResource(R.string.ui_put_back)) }
                    TextButton(onClick = onDeleteLasso) {
                        Text(stringResource(R.string.ui_delete), color = scheme.error)
                    }
                }

                tool.isBrush -> {
                    AssistChip(
                        onClick = onOpenBrushMenu,
                        label = { Text(tool.displayName) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(brushIcon(tool)),
                                contentDescription = stringResource(R.string.ui_select_brush),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    SizeChip("${brushSize.roundToInt()} px")
                    OpacityChip()
                    ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                }

                tool == Tool.ERASER -> {
                    Text(
                        text = stringResource(R.string.ui_eraser),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    SizeChip("${brushSize.roundToInt()} px", inline = true)
                    QuickSizeSlider()
                }

                tool == Tool.FILL -> {
                    AssistChip(
                        onClick = onOpenToolOptions,
                        label = { Text("${stringResource(R.string.ui_fill_tolerance)} $fillTolerance") }
                    )
                    ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                }

                tool == Tool.TEXT -> {
                    Text(
                        text = stringResource(R.string.ui_tap_the_canvas_to_add_text),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                    OpacityChip()
                }

                tool == Tool.LINE || tool == Tool.RECT || tool == Tool.ELLIPSE -> {
                    ActionIconButton(
                        iconRes = WishyIcons.ShapeLine,
                        contentDescription = stringResource(R.string.ui_line),
                        selected = tool == Tool.LINE,
                        onClick = { onSelectShapeTool?.invoke(Tool.LINE) }
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.ShapeRect,
                        contentDescription = stringResource(R.string.ui_rectangle),
                        selected = tool == Tool.RECT,
                        onClick = { onSelectShapeTool?.invoke(Tool.RECT) }
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.ShapeEllipse,
                        contentDescription = stringResource(R.string.ui_ellipse),
                        selected = tool == Tool.ELLIPSE,
                        onClick = { onSelectShapeTool?.invoke(Tool.ELLIPSE) }
                    )
                    SizeChip("${brushSize.roundToInt()} px", inline = true)
                    QuickSizeSlider()
                    ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                }

                // Selection: it has no colour or size, so the bar just says what to do.
                tool == Tool.LASSO -> {
                    Icon(
                        painter = painterResource(WishyIcons.selected(WishyIcons.Lasso)),
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.ui_lasso_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // Eyedropper: show the colour currently held, and how to change it.
                tool == Tool.EYEDROPPER -> {
                    Text(
                        text = stringResource(R.string.ui_eyedropper_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    ColorSwatch(color = Color(color), onClick = onOpenColorPicker, size = 36.dp)
                }

                else -> {
                    Text(
                        text = tool.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
