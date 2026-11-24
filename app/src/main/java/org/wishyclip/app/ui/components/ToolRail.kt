package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.isBrush
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * The floating tool palette: a compact vertical Material 3 surface that sits over the stage
 * (it never takes space away from the canvas). The grip at the top collapses the rail to just the
 * active tool; long-press the rail to move it. [trailing] is pinned at the bottom (the editor puts
 * the color swatch there). [maxHeight] caps the rail so it scrolls instead of overflowing.
 */
@Composable
fun ToolRail(
    selectedTool: Tool,
    onSelectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
    activeBrush: Tool = Tool.PEN,
    onBrushClick: () -> Unit = { onSelectTool(activeBrush) },
    onToolDoubleTap: (Tool) -> Unit = {},
    activeShape: Tool = Tool.LINE,
    mirrorMode: MirrorMode = MirrorMode.OFF,
    onMirrorClick: (() -> Unit)? = null,
    rulerOn: Boolean = false,
    onRulerClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val capped = if (maxHeight == Dp.Unspecified) Modifier else Modifier.heightIn(max = maxHeight)

    GlassSurface(
        modifier = modifier
            .draggablePanel()
            .width(56.dp)
            .then(capped)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Grip: tap to collapse / expand the palette.
            IconButton(
                onClick = { collapsed = !collapsed },
                modifier = Modifier.size(width = 48.dp, height = 32.dp)
            ) {
                Icon(
                    painter = painterResource(WishyIcons.Grip),
                    contentDescription = stringResource(if (collapsed) R.string.ui_expand_tools else R.string.ui_collapse_tools),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToolItems(
                    collapsed, selectedTool, onSelectTool, onToolDoubleTap, activeShape, activeBrush,
                    onBrushClick, mirrorMode, onMirrorClick, rulerOn, onRulerClick
                )
            }
            if (trailing != null) {
                Box(modifier = Modifier.padding(top = 4.dp)) { trailing() }
            }
        }
    }
}

/**
 * Emits the tool buttons. All brushes live behind ONE Brush button (it shows the icon of the
 * active brush and opens the brush menu). When [collapsed] only the active tool stays visible.
 */
@Composable
private fun ToolItems(
    collapsed: Boolean,
    selectedTool: Tool,
    onSelectTool: (Tool) -> Unit,
    onToolDoubleTap: (Tool) -> Unit,
    activeShape: Tool,
    activeBrush: Tool,
    onBrushClick: () -> Unit,
    mirrorMode: MirrorMode,
    onMirrorClick: (() -> Unit)?,
    rulerOn: Boolean,
    onRulerClick: (() -> Unit)?
) {
    val isShape = selectedTool == Tool.LINE || selectedTool == Tool.RECT || selectedTool == Tool.ELLIPSE
    val shapeIcon = when (activeShape) {
        Tool.RECT -> WishyIcons.ShapeRect
        Tool.ELLIPSE -> WishyIcons.ShapeEllipse
        else -> WishyIcons.ShapeLine
    }
    @Composable
    fun Entry(visibleWhenCollapsed: Boolean, content: @Composable () -> Unit) {
        if (!collapsed || visibleWhenCollapsed) content()
    }

    Entry(selectedTool.isBrush) {
        ToolButton(iconRes = brushIcon(activeBrush), description = "Brush", selected = selectedTool.isBrush, onClick = onBrushClick)
    }
    Entry(selectedTool == Tool.ERASER) {
        ToolButton(iconRes = WishyIcons.Eraser, description = "Eraser", selected = selectedTool == Tool.ERASER, onClick = { onSelectTool(Tool.ERASER) }, onDoubleClick = { onToolDoubleTap(Tool.ERASER) })
    }
    Entry(selectedTool == Tool.LASSO) {
        ToolButton(iconRes = WishyIcons.Lasso, description = "Lasso", selected = selectedTool == Tool.LASSO, onClick = { onSelectTool(Tool.LASSO) }, onDoubleClick = { onToolDoubleTap(Tool.LASSO) })
    }
    Entry(selectedTool == Tool.FILL) {
        ToolButton(iconRes = WishyIcons.Fill, description = "Fill", selected = selectedTool == Tool.FILL, onClick = { onSelectTool(Tool.FILL) }, onDoubleClick = { onToolDoubleTap(Tool.FILL) })
    }
    Entry(isShape) {
        ToolButton(iconRes = shapeIcon, description = "Shapes", selected = isShape, onClick = { onSelectTool(activeShape) }, onDoubleClick = { onToolDoubleTap(activeShape) })
    }
    Entry(selectedTool == Tool.TEXT) {
        ToolButton(iconRes = WishyIcons.Text, description = "Text", selected = selectedTool == Tool.TEXT, onClick = { onSelectTool(Tool.TEXT) }, onDoubleClick = { onToolDoubleTap(Tool.TEXT) })
    }
    Entry(selectedTool == Tool.EYEDROPPER) {
        ToolButton(iconRes = WishyIcons.Eyedropper, description = "Eyedropper", selected = selectedTool == Tool.EYEDROPPER, onClick = { onSelectTool(Tool.EYEDROPPER) })
    }
    // Modifiers (not exclusive tools): they stay on while you switch between drawing tools.
    if (!collapsed && (onMirrorClick != null || onRulerClick != null)) {
        HorizontalDivider(
            modifier = Modifier.width(32.dp).padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
    if (!collapsed && onMirrorClick != null) {
        ToolButton(iconRes = WishyIcons.Mirror, description = mirrorMode.label, selected = mirrorMode != MirrorMode.OFF, onClick = onMirrorClick)
    }
    if (!collapsed && onRulerClick != null) {
        ToolButton(iconRes = WishyIcons.Ruler, description = if (rulerOn) "Ruler on" else "Ruler off", selected = rulerOn, onClick = onRulerClick)
    }
}

@Preview(name = "ToolRail Light")
@Composable
private fun ToolRailLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ToolRail(selectedTool = Tool.PEN, onSelectTool = {})
    }
}

@Preview(name = "ToolRail Dark")
@Composable
private fun ToolRailDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ToolRail(selectedTool = Tool.ERASER, onSelectTool = {})
    }
}
