package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

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
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(tokens.surface)
            .padding(tokens.spaceMedium)
    ) {
        SectionHeader(title = "Layers (Current Frame)")
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
        ) {
            items(layers.indices.reversed().toList()) { idx ->
                val layer = layers[idx]
                LayerRow(
                    layer = layer,
                    selected = idx == activeLayerIndex,
                    onSelect = { onSelectLayer(idx) },
                    onToggleVisibility = { onToggleVisibility(idx, it) },
                    onOpacityChange = { onOpacityChange(idx, it) },
                    onMoveUp = { onMoveLayer(idx, 1) },
                    onMoveDown = { onMoveLayer(idx, -1) },
                    onDelete = { onDeleteLayer(idx) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spaceSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAddLayer) {
                Text("+ Add Layer", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onImportImageLayer) {
                Text("🖼 Import Image", style = MaterialTheme.typography.labelLarge)
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
