package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.isBrush
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * The tool selector. With [horizontal] = false it is a vertical rail (landscape / tablets); with
 * [horizontal] = true it is a bar that sits above the timeline (portrait phones).
 * [trailing] is pinned at the end of the bar (the editor puts the color swatch there).
 */
@Composable
fun ToolRail(
    selectedTool: Tool,
    onSelectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    activeBrush: Tool = Tool.PEN,
    onBrushClick: () -> Unit = { onSelectTool(activeBrush) },
    mirrorMode: MirrorMode = MirrorMode.OFF,
    onMirrorClick: (() -> Unit)? = null,
    rulerOn: Boolean = false,
    onRulerClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = WishyTheme.tokens

    if (horizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(tokens.toolRail)
                .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
            ) {
                ToolItems(selectedTool, onSelectTool, activeBrush, onBrushClick, mirrorMode, onMirrorClick, rulerOn, onRulerClick)
            }
            if (trailing != null) {
                Row(modifier = Modifier.padding(start = tokens.spaceSmall)) { trailing() }
            }
        }
    } else {
        Column(
            modifier = modifier
                .width(tokens.toolRailWidth)
                .fillMaxHeight()
                .background(tokens.toolRail)
                .padding(vertical = tokens.spaceSmall),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
            ) {
                ToolItems(selectedTool, onSelectTool, activeBrush, onBrushClick, mirrorMode, onMirrorClick, rulerOn, onRulerClick)
            }
            if (trailing != null) {
                Column(modifier = Modifier.padding(top = tokens.spaceSmall)) { trailing() }
            }
        }
    }
}

/**
 * Emits the tool buttons. All brushes live behind ONE Brush button (it shows the icon of the
 * active brush and opens the separate Brush menu), like FlipaClip.
 */
@Composable
private fun ToolItems(
    selectedTool: Tool,
    onSelectTool: (Tool) -> Unit,
    activeBrush: Tool,
    onBrushClick: () -> Unit,
    mirrorMode: MirrorMode,
    onMirrorClick: (() -> Unit)?,
    rulerOn: Boolean,
    onRulerClick: (() -> Unit)?
) {
    ToolButton(iconRes = brushIcon(activeBrush), description = "Brush", selected = selectedTool.isBrush, onClick = onBrushClick)
    ToolButton(iconRes = WishyIcons.Eraser, description = "Eraser", selected = selectedTool == Tool.ERASER, onClick = { onSelectTool(Tool.ERASER) })
    ToolButton(iconRes = WishyIcons.Lasso, description = "Lasso", selected = selectedTool == Tool.LASSO, onClick = { onSelectTool(Tool.LASSO) })
    ToolButton(iconRes = WishyIcons.Fill, description = "Fill", selected = selectedTool == Tool.FILL, onClick = { onSelectTool(Tool.FILL) })
    ToolButton(
        iconRes = WishyIcons.Shapes,
        description = "Shapes",
        selected = selectedTool == Tool.LINE || selectedTool == Tool.RECT || selectedTool == Tool.ELLIPSE,
        onClick = { onSelectTool(Tool.LINE) }
    )
    ToolButton(iconRes = WishyIcons.Text, description = "Text", selected = selectedTool == Tool.TEXT, onClick = { onSelectTool(Tool.TEXT) })
    ToolButton(iconRes = WishyIcons.Eyedropper, description = "Eyedropper", selected = selectedTool == Tool.EYEDROPPER, onClick = { onSelectTool(Tool.EYEDROPPER) })
    // Modifiers (not exclusive tools): they stay on while you switch between drawing tools.
    if (onMirrorClick != null) {
        ToolButton(iconRes = WishyIcons.Mirror, description = mirrorMode.label, selected = mirrorMode != MirrorMode.OFF, onClick = onMirrorClick)
    }
    if (onRulerClick != null) {
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

@Preview(name = "ToolBar Dark (horizontal)")
@Composable
private fun ToolBarDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ToolRail(selectedTool = Tool.ERASER, onSelectTool = {}, horizontal = true)
    }
}
