package org.wishyclip.app.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * The layer list. The top row is the top of the stack (like FlipaClip). Each row shows a real
 * thumbnail; drag the handle to reorder (a primary bar shows where the layer will land); the
 * selected layer's opacity is edited in the footer. With [embedded] the panel draws no surface of
 * its own, so it can live inside a bottom sheet.
 */
@Composable
fun LayerPanel(
    layers: List<LayerUi>,
    activeLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onImportImageLayer: () -> Unit,
    onToggleVisibility: (Int, Boolean) -> Unit,
    onOpacityChange: (Int, Float) -> Unit,
    onMoveLayer: (Int, Int) -> Unit,
    onDeleteLayer: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onToggleLock: (Int, Boolean) -> Unit = { _, _ -> },
    onCycleBlend: (Int) -> Unit = {},
    onDuplicateLayer: (Int) -> Unit = {},
    onMergeDown: (Int) -> Unit = {},
    onRenameLayer: (Int) -> Unit = {},
    /** Cap for the list; the editor lowers it in short (landscape) windows. */
    maxListHeight: Dp = 280.dp,
    /** Layer id -> preview of its pixels (missing = empty layer). */
    thumbnails: Map<Long, ImageBitmap> = emptyMap(),
    embedded: Boolean = false
) {
    val body: @Composable () -> Unit = {
        LayerPanelBody(
            layers, activeLayerIndex, onSelectLayer, onAddLayer, onImportImageLayer, onToggleVisibility,
            onOpacityChange, onMoveLayer, onDeleteLayer, onToggleLock, onCycleBlend, onDuplicateLayer,
            onMergeDown, onRenameLayer, maxListHeight, thumbnails
        )
    }
    if (embedded) {
        Box(modifier = modifier) { body() }
    } else {
        GlassSurface(modifier = modifier.draggablePanel()) { body() }
    }
}

@Composable
private fun LayerPanelBody(
    layers: List<LayerUi>,
    activeLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onImportImageLayer: () -> Unit,
    onToggleVisibility: (Int, Boolean) -> Unit,
    onOpacityChange: (Int, Float) -> Unit,
    onMoveLayer: (Int, Int) -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onToggleLock: (Int, Boolean) -> Unit,
    onCycleBlend: (Int) -> Unit,
    onDuplicateLayer: (Int) -> Unit,
    onMergeDown: (Int) -> Unit,
    onRenameLayer: (Int) -> Unit,
    maxListHeight: Dp,
    thumbnails: Map<Long, ImageBitmap>
) {
    val rowPx = with(LocalDensity.current) { LayerRowHeight.toPx() }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val order = layers.indices.reversed().toList()
    val liveSteps = if (dragIndex >= 0) (-dragOffset / rowPx).roundToInt() else 0
    val dropTarget = if (dragIndex >= 0) (dragIndex + liveSteps).coerceIn(0, layers.lastIndex) else -1

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_layers),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.ui_layers_all_frames),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
        ) {
            itemsIndexed(order, key = { _, idx -> layers[idx].id }) { _, idx ->
                val layer = layers[idx]
                val isDragged = idx == dragIndex
                val indicator = when {
                    dragIndex < 0 || isDragged || idx != dropTarget -> DropIndicator.NONE
                    liveSteps > 0 -> DropIndicator.ABOVE
                    else -> DropIndicator.BELOW
                }
                val handle = Modifier.pointerInput(layer.id, idx, layers.size) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragIndex = idx
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            val steps = (-dragOffset / rowPx).roundToInt()
                            val dir = if (steps > 0) 1 else -1
                            var cur = idx
                            repeat(abs(steps)) {
                                if (cur + dir in layers.indices) {
                                    onMoveLayer(cur, dir)
                                    cur += dir
                                }
                            }
                            dragIndex = -1
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            dragIndex = -1
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragOffset += dy
                        }
                    )
                }
                LayerRow(
                    layer = layer,
                    selected = idx == activeLayerIndex,
                    onSelect = { onSelectLayer(idx) },
                    onToggleVisibility = { onToggleVisibility(idx, it) },
                    onOpacityChange = { onOpacityChange(idx, it) },
                    onMoveUp = { onMoveLayer(idx, 1) },
                    onMoveDown = { onMoveLayer(idx, -1) },
                    onDelete = { onDeleteLayer(idx) },
                    onToggleLock = { onToggleLock(idx, it) },
                    onCycleBlend = { onCycleBlend(idx) },
                    onDuplicate = { onDuplicateLayer(idx) },
                    onMergeDown = if (idx > 0) ({ onMergeDown(idx) }) else null,
                    onRename = { onRenameLayer(idx) },
                    thumbnail = thumbnails[layer.id],
                    handleModifier = handle,
                    dragging = isDragged,
                    dropIndicator = indicator,
                    modifier = if (isDragged) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffset }
                    } else Modifier
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        val active = layers.getOrNull(activeLayerIndex)
        if (active != null) {
            WishySlider(
                value = active.opacity * 100f,
                onValueChange = { onOpacityChange(activeLayerIndex, it / 100f) },
                valueRange = 0f..100f,
                label = stringResource(R.string.ui_opacity),
                unit = "%",
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onAddLayer) {
                Text(stringResource(R.string.ui_add_layer))
            }
            TextButton(onClick = onImportImageLayer) {
                Text(stringResource(R.string.ui_import_image))
            }
        }
    }
}

@Preview(name = "LayerPanel Light")
@Composable
private fun LayerPanelLightPreview() {
    WishyTheme(tokens = LightTokens) {
        LayerPanel(
            layers = listOf(
                LayerUi(1, "Layer 1", visible = true, opacity = 1f),
                LayerUi(2, "Background", visible = true, opacity = 0.8f)
            ),
            activeLayerIndex = 0,
            onSelectLayer = {},
            onAddLayer = {},
            onImportImageLayer = {},
            onToggleVisibility = { _, _ -> },
            onOpacityChange = { _, _ -> },
            onMoveLayer = { _, _ -> },
            onDeleteLayer = {}
        )
    }
}

@Preview(name = "LayerPanel Dark")
@Composable
private fun LayerPanelDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        LayerPanel(
            layers = listOf(LayerUi(1, "Layer 1", visible = true, opacity = 1f)),
            activeLayerIndex = 0,
            onSelectLayer = {},
            onAddLayer = {},
            onImportImageLayer = {},
            onToggleVisibility = { _, _ -> },
            onOpacityChange = { _, _ -> },
            onMoveLayer = { _, _ -> },
            onDeleteLayer = {}
        )
    }
}
