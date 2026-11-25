package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/** Where the dragged layer would land relative to this row (drawn as a primary insertion bar). */
enum class DropIndicator { NONE, ABOVE, BELOW }

/** Fixed height of a layer row; the panel's drag-to-reorder maths relies on it. */
val LayerRowHeight = 64.dp

/**
 * One layer: drag handle, a real thumbnail of its drawing, name with opacity / blend subtitle, a
 * visibility toggle and an overflow menu (rename, duplicate, lock, blend, move, merge, delete).
 * Selecting is a tap on the row; frames are selected elsewhere (timeline), never here.
 */
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
    onDuplicate: (() -> Unit)? = null,
    /** Null for the bottom layer (nothing below to merge into). */
    onMergeDown: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    thumbnail: ImageBitmap? = null,
    /** Pointer-input modifier that makes the handle start a reorder drag (empty = no handle gesture). */
    handleModifier: Modifier = Modifier,
    dragging: Boolean = false,
    dropIndicator: DropIndicator = DropIndicator.NONE
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val contentAlpha = if (layer.visible) 1f else 0.5f

    Box(modifier = modifier.fillMaxWidth().height(LayerRowHeight)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(LayerRowHeight),
            shape = MaterialTheme.shapes.medium,
            color = if (selected) scheme.secondaryContainer else Color.Transparent,
            shadowElevation = if (dragging) 6.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onSelect)
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(width = 32.dp, height = LayerRowHeight).then(handleModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(WishyIcons.Grip),
                        contentDescription = stringResource(R.string.ui_drag_to_reorder),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                LayerThumbnail(
                    image = thumbnail,
                    selected = selected,
                    dimmed = !layer.visible,
                    modifier = Modifier.size(width = 64.dp, height = 40.dp)
                )
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = (if (selected) scheme.onSecondaryContainer else scheme.onSurface).copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Lock state is visible on the row itself; toggle it from the overflow menu.
                        if (layer.locked) {
                            Icon(
                                painter = painterResource(WishyIcons.selected(WishyIcons.Lock)),
                                contentDescription = stringResource(R.string.ui_locked),
                                tint = scheme.primary,
                                modifier = Modifier.padding(end = 4.dp).size(14.dp)
                            )
                        }
                        Text(
                            text = "${(layer.opacity * 100f).roundToInt()}% · ${layer.blendMode.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant.copy(alpha = contentAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ActionIconButton(
                    iconRes = if (layer.visible) WishyIcons.VisibilityOn else WishyIcons.VisibilityOff,
                    contentDescription = stringResource(R.string.ui_visibility),
                    onClick = { onToggleVisibility(!layer.visible) }
                )
                Box {
                    ActionIconButton(
                        iconRes = WishyIcons.More,
                        contentDescription = stringResource(R.string.ui_layer_actions),
                        onClick = { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_rename_layer)) },
                                onClick = { menuOpen = false; onRename() }
                            )
                        }
                        if (onDuplicate != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_duplicate_layer)) },
                                onClick = { menuOpen = false; onDuplicate() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (layer.locked) "Unlock layer" else "Lock layer") },
                            onClick = { menuOpen = false; onToggleLock(!layer.locked) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_move_up)) },
                            onClick = { menuOpen = false; onMoveUp() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_move_down)) },
                            onClick = { menuOpen = false; onMoveDown() }
                        )
                        if (onMergeDown != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_merge_down)) },
                                onClick = { menuOpen = false; onMergeDown() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_delete_layer), color = scheme.error) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
        if (dropIndicator != DropIndicator.NONE) {
            Box(
                modifier = Modifier
                    .align(if (dropIndicator == DropIndicator.ABOVE) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(scheme.primary, MaterialTheme.shapes.extraSmall)
            )
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
