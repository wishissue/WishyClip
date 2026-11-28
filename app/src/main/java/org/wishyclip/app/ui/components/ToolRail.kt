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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * active tool; long-press the rail to move it. Tap a tool to select it; tap the selected tool again for its settings. [trailing] is pinned at the bottom (the editor puts
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
    trailing: (@Composable () -> Unit)? = null,
    /** Ids (see [PinnableTool]) of tools that stay visible while the rail is minimized. */
    pinnedTools: Set<String> = emptySet(),
    /** Shrink the rail by itself after [AutoMinimizeMs] without switching tools. */
    autoMinimize: Boolean = true
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    // Touching the rail (opening a tool's settings, scrolling it) postpones the auto-minimize.
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTool, collapsed, touches, autoMinimize) {
        if (autoMinimize && !collapsed) {
            delay(AutoMinimizeMs)
            collapsed = true
        }
    }
    val capped = if (maxHeight == Dp.Unspecified) Modifier else Modifier.heightIn(max = maxHeight)

    GlassSurface(
        modifier = modifier
            .draggablePanel()
            .width(56.dp)
            .then(capped)
            .pointerInput(Unit) {
                // Observe only (Initial pass, nothing consumed): any touch on the rail restarts the idle timer.
                awaitEachGesture {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touches++
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp).animateContentSize(),
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
                    onBrushClick, mirrorMode, onMirrorClick, rulerOn, onRulerClick, pinnedTools
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
    onRulerClick: (() -> Unit)?,
    pinned: Set<String>
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

    Entry(selectedTool.isBrush || "brush" in pinned) {
        ToolButton(iconRes = brushIcon(activeBrush), description = "Brush", selected = selectedTool.isBrush, onClick = onBrushClick)
    }
    Entry(selectedTool == Tool.ERASER || "eraser" in pinned) {
        ToolButton(iconRes = WishyIcons.Eraser, description = "Eraser", selected = selectedTool == Tool.ERASER, onClick = { if (selectedTool == Tool.ERASER) onToolDoubleTap(Tool.ERASER) else onSelectTool(Tool.ERASER) })
    }
    Entry(selectedTool == Tool.LASSO || "lasso" in pinned) {
        ToolButton(iconRes = WishyIcons.Lasso, description = "Lasso", selected = selectedTool == Tool.LASSO, onClick = { if (selectedTool == Tool.LASSO) onToolDoubleTap(Tool.LASSO) else onSelectTool(Tool.LASSO) })
    }
    Entry(selectedTool == Tool.FILL || "fill" in pinned) {
        ToolButton(iconRes = WishyIcons.Fill, description = "Fill", selected = selectedTool == Tool.FILL, onClick = { if (selectedTool == Tool.FILL) onToolDoubleTap(Tool.FILL) else onSelectTool(Tool.FILL) })
    }
    Entry(isShape || "shapes" in pinned) {
        ToolButton(iconRes = shapeIcon, description = "Shapes", selected = isShape, onClick = { if (isShape) onToolDoubleTap(activeShape) else onSelectTool(activeShape) })
    }
    Entry(selectedTool == Tool.TEXT || "text" in pinned) {
        ToolButton(iconRes = WishyIcons.Text, description = "Text", selected = selectedTool == Tool.TEXT, onClick = { if (selectedTool == Tool.TEXT) onToolDoubleTap(Tool.TEXT) else onSelectTool(Tool.TEXT) })
    }
    Entry(selectedTool == Tool.EYEDROPPER || "eyedropper" in pinned) {
        ToolButton(iconRes = WishyIcons.Eyedropper, description = "Eyedropper", selected = selectedTool == Tool.EYEDROPPER, onClick = { onSelectTool(Tool.EYEDROPPER) })
    }
    // Modifiers (not exclusive tools): they stay on while you switch between drawing tools. When the rail is
    // minimized they remain visible if they are switched on (so an active mirror/ruler is never hidden) or pinned.
    val showMirror = onMirrorClick != null && (!collapsed || mirrorMode != MirrorMode.OFF || "mirror" in pinned)
    val showRuler = onRulerClick != null && (!collapsed || rulerOn || "ruler" in pinned)
    if (!collapsed && (onMirrorClick != null || onRulerClick != null)) {
        HorizontalDivider(
            modifier = Modifier.width(32.dp).padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
    if (showMirror) {
        ToolButton(iconRes = WishyIcons.Mirror, description = mirrorMode.label, selected = mirrorMode != MirrorMode.OFF, onClick = onMirrorClick!!)
    }
    if (showRuler) {
        ToolButton(iconRes = WishyIcons.Ruler, description = if (rulerOn) "Ruler on" else "Ruler off", selected = rulerOn, onClick = onRulerClick!!)
    }
}

private const val AutoMinimizeMs = 3000L

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
