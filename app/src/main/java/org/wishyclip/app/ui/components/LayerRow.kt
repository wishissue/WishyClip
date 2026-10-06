package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.model.LayerBlend
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun LayerRow(
    layer: LayerUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleLock: (Boolean) -> Unit = {},
    onCycleBlend: () -> Unit = {},
    /** Null for the bottom layer (nothing below to merge into). */
    onMergeDown: (() -> Unit)? = null
) {
    val tokens = WishyTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(if (selected) tokens.primaryContainer else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(tokens.spaceXs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconButton(
                iconRes = if (layer.visible) WishyIcons.VisibilityOn else WishyIcons.VisibilityOff,
                contentDescription = "Visibility",
                onClick = { onToggleVisibility(!layer.visible) }
            )
            ActionIconButton(
                iconRes = WishyIcons.Lock,
                contentDescription = if (layer.locked) "Unlock layer" else "Lock layer",
                selected = layer.locked,
                tint = if (layer.locked) null else tokens.onSurfaceVariant.copy(alpha = 0.45f),
                onClick = { onToggleLock(!layer.locked) }
            )
            Text(
                text = layer.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) tokens.onPrimaryContainer else tokens.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = tokens.spaceSmall)
            )
            Text(
                text = "▲",
                modifier = Modifier.clickable { onMoveUp() }.padding(tokens.spaceSmall),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.onSurfaceVariant
            )
            Text(
                text = "▼",
                modifier = Modifier.clickable { onMoveDown() }.padding(tokens.spaceSmall),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.onSurfaceVariant
            )
            ActionIconButton(
                iconRes = WishyIcons.Delete,
                contentDescription = "Delete Layer",
                onClick = onDelete
            )
        }
        if (selected) {
            WishySlider(
                value = layer.opacity * 100f,
                onValueChange = { onOpacityChange(it / 100f) },
                valueRange = 0f..100f,
                label = "Opacity",
                unit = "%",
                modifier = Modifier.padding(horizontal = tokens.spaceSmall)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCycleBlend) {
                    Text(
                        text = "Blend: ${layer.blendMode.label}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (onMergeDown != null) {
                    TextButton(onClick = onMergeDown) {
                        Text("Merge down", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Preview(name = "LayerRow Light")
@Composable
private fun LayerRowLightPreview() {
    WishyTheme(tokens = LightTokens) {
        LayerRow(
            layer = LayerUi(1, "Layer 1", visible = true, opacity = 1f),
            selected = true,
            onSelect = {},
            onToggleVisibility = {},
            onOpacityChange = {},
            onMoveUp = {},
            onMoveDown = {},
            onDelete = {}
        )
    }
}

@Preview(name = "LayerRow Dark")
@Composable
private fun LayerRowDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        LayerRow(
            layer = LayerUi(2, "Background", visible = false, opacity = 0.5f),
            selected = false,
            onSelect = {},
            onToggleVisibility = {},
            onOpacityChange = {},
            onMoveUp = {},
            onMoveDown = {},
            onDelete = {}
        )
    }
}
