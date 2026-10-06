package org.wishyclip.app.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.canvas.CanvasViewState
import org.wishyclip.app.canvas.DrawingCanvas
import org.wishyclip.app.export.ExportFormat
import org.wishyclip.app.export.ExportService
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.displayName
import org.wishyclip.app.model.isBrush
import org.wishyclip.app.ui.components.BrushMenu
import org.wishyclip.app.ui.EditorViewModel
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ColorPickerSheet
import org.wishyclip.app.ui.components.ColorSwatch
import org.wishyclip.app.ui.components.ContextToolPanel
import org.wishyclip.app.ui.components.GlassSurface
import org.wishyclip.app.ui.components.LayerPanel
import org.wishyclip.app.ui.components.ShortcutDialog
import org.wishyclip.app.ui.components.StudioTimeline
import org.wishyclip.app.ui.components.TimelineStrip
import org.wishyclip.app.ui.components.ToolRail
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.components.WishySlider
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onExit: () -> Unit,
    isLeftHanded: Boolean = false,
    onOpenDesignGallery: (() -> Unit)? = null
) {
    val tokens = WishyTheme.tokens
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.onHostStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { vm.saveAndExit(onExit) }

    // Saveable so panels / canvas view survive a device rotation or process recreation.
    var isUiHidden by rememberSaveable { mutableStateOf(false) }
    var showLayersPanel by rememberSaveable { mutableStateOf(false) }
    val canvasView = rememberSaveable(saver = CanvasViewState.Saver) { CanvasViewState() }
    var showColorPicker by remember { mutableStateOf(false) }
    var showToolOptions by rememberSaveable { mutableStateOf(false) }
    var showBrushMenu by rememberSaveable { mutableStateOf(false) }
    var activeShape by rememberSaveable { mutableStateOf(Tool.LINE) }
    var activeBrush by rememberSaveable { mutableStateOf(if (vm.tool.isBrush) vm.tool else Tool.PEN) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteFrameDialog by remember { mutableStateOf(false) }
    var showOnionSkinDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var isStudioExpanded by rememberSaveable { mutableStateOf(false) }

    val imageLayerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importImageForPlacement(uri)
    }
    val imageSequencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) vm.importImageSequence(uris)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importVideoAsFrames(uri)
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Five big top-bar buttons leave no room for the title on narrow phones.
    val showTitle = LocalConfiguration.current.screenWidthDp >= 420
    val brushPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.importBrushFiles(uris)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addAudioTrackFromUri(uri, name = "Audio ${vm.audioTracks.size + 1}")
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val onSelectTool: (Tool) -> Unit = { selected ->
        showBrushMenu = false
        showToolOptions = false
        if (vm.tool != selected) vm.selectTool(selected)
        if (selected == Tool.LINE || selected == Tool.RECT || selected == Tool.ELLIPSE) activeShape = selected
    }
    // Double tap on a tool opens its options (single tap just switches, so drawing is never blocked).
    val onToolDoubleTap: (Tool) -> Unit = { selected ->
        showBrushMenu = false
        if (vm.tool != selected) vm.selectTool(selected)
        showToolOptions = selected != Tool.EYEDROPPER && !showToolOptions
    }
    // One Brush button opens the separate brush menu (FlipaClip-style).
    val onBrushClick: () -> Unit = {
        showToolOptions = false
        if (vm.tool.isBrush) {
            // Second tap on the active brush opens the brush menu; first tap only selects it.
            showBrushMenu = !showBrushMenu
        } else {
            vm.selectTool(activeBrush)
            showBrushMenu = false
        }
    }
    // The color chip lives at the end of the tool bar/rail, like in other animation apps.
    val colorSwatchSlot: @Composable () -> Unit = {
        ColorSwatch(
            color = Color(vm.color),
            onClick = { showColorPicker = true },
            size = 48.dp
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = tokens.canvasBackdrop
    ) {
        if (vm.loadError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not open this project.", color = tokens.onSurface)
                    TextButton(onClick = onExit) { Text("Back") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // ---- Top bar: back, title, undo/redo, play, layers, more ----
                AnimatedVisibility(
                    visible = !isUiHidden,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                  // Floating glass capsule: big Back, Undo, Redo, Layers and More buttons.
                  Box(
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = tokens.floatMargin, vertical = tokens.floatMargin / 2)
                  ) {
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tokens.topBarHeight)
                            .padding(horizontal = tokens.spaceXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconButton(
                            iconRes = WishyIcons.Back,
                            contentDescription = "Back",
                            onClick = { vm.saveAndExit(onExit) }
                        )
                        if (showTitle) {
                            Text(
                                text = vm.project?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = tokens.spaceSmall)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        ActionIconButton(
                            iconRes = WishyIcons.Undo,
                            contentDescription = stringResource(R.string.action_undo),
                            enabled = vm.canUndo,
                            onClick = { vm.undo() }
                        )
                        ActionIconButton(
                            iconRes = WishyIcons.Redo,
                            contentDescription = stringResource(R.string.action_redo),
                            enabled = vm.canRedo,
                            onClick = { vm.redo() }
                        )
                        ActionIconButton(
                            iconRes = WishyIcons.Layers,
                            contentDescription = "Layers",
                            selected = showLayersPanel,
                            onClick = { showLayersPanel = !showLayersPanel }
                        )
                        Box {
                            ActionIconButton(
                                iconRes = WishyIcons.More,
                                contentDescription = "More Options",
                                onClick = { showOverflowMenu = true }
                            )
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export Animation") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Media") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Onion Skin Settings") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showOnionSkinDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hide interface") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showBrushMenu = false
                                        showToolOptions = false
                                        showLayersPanel = false
                                        isUiHidden = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rotate Canvas 90°") },
                                    onClick = {
                                        showOverflowMenu = false
                                        canvasView.rotateBy(90f)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset View") },
                                    onClick = {
                                        showOverflowMenu = false
                                        canvasView.reset()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Keyboard Shortcuts") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showShortcutDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Brushes") },
                                    onClick = {
                                        showOverflowMenu = false
                                        brushPicker.launch("*/*")
                                    }
                                )
                                if (onOpenDesignGallery != null) {
                                    DropdownMenuItem(
                                        text = { Text("Design Gallery (Debug)") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onOpenDesignGallery()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                  }
                }

                // ---- Workspace: (landscape tool rail) + canvas ----
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLandscape && !isLeftHanded && !isUiHidden) {
                        ToolRail(
                            selectedTool = vm.tool,
                            onSelectTool = onSelectTool,
                            activeBrush = activeBrush,
                            onBrushClick = onBrushClick,
                            onToolDoubleTap = onToolDoubleTap,
                            activeShape = activeShape,
                            mirrorMode = vm.mirrorMode,
                            onMirrorClick = { vm.cycleMirror() },
                            rulerOn = vm.rulerVisible,
                            onRulerClick = { vm.toggleRuler() },
                            trailing = colorSwatchSlot
                        )
                    }

                    // Canvas area (BoxWithConstraints: floating panels are capped to the window height)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(tokens.canvasBackdrop)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showBrushMenu = false
                                        showToolOptions = false
                                        showLayersPanel = false
                                    },
                                    onDoubleTap = {
                                        // Double-tap quick undo
                                        vm.undo()
                                    }
                                )
                            }
                    ) {
                        val p = vm.project
                        if (p != null && vm.loaded) {
                            DrawingCanvas(
                                projectWidth = p.width,
                                projectHeight = p.height,
                                layers = { vm.currentLayers() },
                                onionSkinData = { vm.getOnionSkinData() },
                                activeLassoSelection = { vm.activeLassoSelection },
                                strokeOverlay = { vm.activeStrokeOverlay() },
                                lassoPreview = { vm.lassoPreviewPath() },
                                revision = { vm.revision },
                                enabled = !vm.isPlaying,
                                view = canvasView,
                                mirror = { vm.mirrorMode },
                                ruler = { vm.activeRuler() },
                                onRulerChange = { vm.updateRuler(it) },
                                onStrokeStart = { x, y, press, tilt -> vm.strokeStart(x, y, press, tilt) },
                                onStrokeMove = { x, y, press, tilt -> vm.strokeMove(x, y, press, tilt) },
                                onStrokeEnd = { vm.strokeEnd() },
                                onStrokeCancel = { vm.strokeCancel() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = tokens.primary)
                            }
                        }

                        // Outside-tap scrim: while a menu is open, tapping the canvas closes it.
                        if (!isUiHidden && (showBrushMenu || showToolOptions || showLayersPanel)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            showBrushMenu = false
                                            showToolOptions = false
                                            showLayersPanel = false
                                        })
                                    }
                            )
                        }

                        // Always-available way back from full screen.
                        if (isUiHidden) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(tokens.spaceSmall)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tokens.surface.copy(alpha = 0.6f))
                                    .clickable { isUiHidden = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(WishyIcons.RestoreUi),
                                    contentDescription = "Show interface",
                                    tint = tokens.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Floating Contextual Bar tailored to active tool
                        if (!isUiHidden && !showBrushMenu) {
                            ContextToolPanel(
                                tool = vm.tool,
                                color = vm.color,
                                brushSize = vm.brushSize,
                                opacity = vm.opacity,
                                fillTolerance = vm.fillTolerance,
                                onSizeChange = { vm.updateBrushSize(it) },
                                onOpacityChange = { vm.updateOpacity(it) },
                                onToleranceChange = { vm.updateFillTolerance(it) },
                                onOpenColorPicker = { showColorPicker = true },
                                onOpenBrushMenu = { showBrushMenu = true },
                                activeLasso = vm.activeLassoSelection,
                                onCommitLasso = { vm.commitLassoSelection() },
                                onCancelLasso = { vm.cancelLassoSelection() },
                                onDeleteLasso = { vm.deleteLassoSelection() },
                                onSelectShapeTool = { vm.selectTool(it) },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = tokens.spaceSmall)
                            )
                        }

                        // Tool options: floats just above the tool bar (portrait) or beside the rail (landscape)
                        if (showBrushMenu && vm.tool.isBrush && !isUiHidden) {
                            BrushMenu(
                                selectedBrush = vm.tool,
                                onSelectBrush = {
                                    activeBrush = it
                                    vm.selectTool(it)
                                },
                                color = vm.color,
                                size = vm.brushSize,
                                onSizeChange = { vm.updateBrushSize(it) },
                                opacity = vm.opacity,
                                onOpacityChange = { vm.updateOpacity(it) },
                                stabilizer = vm.stabilizer,
                                onStabilizerChange = { vm.updateStabilizer(it) },
                                onSettingsFinished = { vm.persistBrush() },
                                customBrushes = vm.customBrushes,
                                selectedCustomId = vm.activeCustomBrushId,
                                onSelectCustom = {
                                    activeBrush = Tool.CUSTOM
                                    vm.selectCustomBrush(it)
                                },
                                onDeleteCustom = {
                                    vm.deleteCustomBrush(it)
                                    if (activeBrush == Tool.CUSTOM) activeBrush = Tool.PEN
                                },
                                onImportBrushes = { brushPicker.launch("*/*") },
                                modifier = Modifier
                                    .align(
                                        when {
                                            !isLandscape -> Alignment.BottomCenter
                                            isLeftHanded -> Alignment.TopEnd
                                            else -> Alignment.TopStart
                                        }
                                    )
                                    .padding(tokens.spaceSmall)
                                    .then(
                                        if (isLandscape) Modifier.width(300.dp)
                                        else Modifier.fillMaxWidth().widthIn(max = 380.dp)
                                    )
                                    .heightIn(max = maxHeight - tokens.spaceSmall * 2)
                            )
                        }

                        if (showToolOptions && !vm.tool.isBrush && !isUiHidden) {
                            GlassSurface(
                                modifier = Modifier
                                    .align(
                                        when {
                                            !isLandscape -> Alignment.BottomCenter
                                            isLeftHanded -> Alignment.TopEnd
                                            else -> Alignment.TopStart
                                        }
                                    )
                                    .padding(tokens.spaceSmall)
                                    .then(
                                        if (isLandscape) Modifier.width(240.dp)
                                        else Modifier.fillMaxWidth().widthIn(max = 380.dp)
                                    )
                                    .heightIn(max = maxHeight - tokens.spaceSmall * 2),
                                shape = RoundedCornerShape(tokens.largeRadius)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(tokens.spaceMedium),
                                    verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                                ) {
                                    Text(
                                        text = vm.tool.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.onSurface
                                    )
                                    if (vm.tool == Tool.FILL) {
                                        WishySlider(
                                            value = vm.fillTolerance.toFloat(),
                                            onValueChange = { vm.updateFillTolerance(it.roundToInt()) },
                                            valueRange = 0f..128f,
                                            label = "Tolerance"
                                        )
                                    } else {
                                        WishySlider(
                                            value = vm.brushSize,
                                            onValueChange = { vm.updateBrushSize(it) },
                                            valueRange = 1f..60f,
                                            label = "Size",
                                            unit = "px",
                                            onValueChangeFinished = { vm.persistBrush() }
                                        )
                                    }
                                    WishySlider(
                                        value = vm.opacity * 100f,
                                        onValueChange = { vm.updateOpacity(it / 100f) },
                                        valueRange = 5f..100f,
                                        label = "Opacity",
                                        unit = "%",
                                        onValueChangeFinished = { vm.persistBrush() }
                                    )
                                    WishySlider(
                                        value = vm.stabilizer * 100f,
                                        onValueChange = { vm.updateStabilizer(it / 100f) },
                                        valueRange = 0f..100f,
                                        label = "Stabilizer",
                                        unit = "%"
                                    )

                                    // Live brush stroke preview (on paper, like the real canvas)
                                    val brushColor = Color(vm.color)
                                    val bSize = vm.brushSize
                                    val bOp = vm.opacity
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(tokens.smallRadius))
                                            .background(tokens.paper)
                                    ) {
                                        val centerY = size.height / 2f
                                        drawLine(
                                            color = brushColor,
                                            start = Offset(20f, centerY),
                                            end = Offset(size.width - 20f, centerY),
                                            strokeWidth = bSize,
                                            cap = StrokeCap.Round,
                                            alpha = bOp
                                        )
                                    }
                                }
                            }
                        }

                        // Layers panel floats over the canvas instead of squeezing it
                        if (showLayersPanel && !isUiHidden) {
                            LayerPanel(
                                layers = vm.layerUi,
                                activeLayerIndex = vm.activeLayerIndex,
                                onSelectLayer = { vm.selectLayer(it) },
                                onAddLayer = { vm.addLayer() },
                                onImportImageLayer = { imageLayerPicker.launch("image/*") },
                                onToggleVisibility = { idx, vis -> vm.setLayerVisible(idx, vis) },
                                onOpacityChange = { idx, op -> vm.setLayerOpacity(idx, op) },
                                onMoveLayer = { idx, dir -> vm.moveLayer(idx, dir) },
                                onDeleteLayer = { idx -> vm.deleteLayer(idx) },
                                onToggleLock = { idx, locked -> vm.setLayerLocked(idx, locked) },
                                onCycleBlend = { idx -> vm.layerUi.getOrNull(idx)?.let { vm.setLayerBlend(idx, it.blendMode.next()) } },
                                onDuplicateLayer = { idx -> vm.duplicateLayer(idx) },
                                onMergeDown = { idx -> vm.mergeLayerDown(idx) },
                                maxListHeight = (maxHeight - 140.dp).coerceIn(80.dp, 280.dp),
                                modifier = Modifier
                                    .align(if (isLeftHanded) Alignment.TopStart else Alignment.TopEnd)
                                    .padding(tokens.spaceSmall)
                                    .width(260.dp)
                            )
                        }

                        // Brief status text (locked layer, brush import result, mirror mode, ...)
                        vm.message?.let { msg ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = tokens.spaceMedium),
                                shape = RoundedCornerShape(20.dp),
                                color = tokens.surface,
                                shadowElevation = tokens.elevationMedium
                            ) {
                                Text(
                                    text = msg,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.onSurface
                                )
                            }
                        }
                    }

                    if (isLandscape && isLeftHanded && !isUiHidden) {
                        ToolRail(
                            selectedTool = vm.tool,
                            onSelectTool = onSelectTool,
                            activeBrush = activeBrush,
                            onBrushClick = onBrushClick,
                            onToolDoubleTap = onToolDoubleTap,
                            activeShape = activeShape,
                            mirrorMode = vm.mirrorMode,
                            onMirrorClick = { vm.cycleMirror() },
                            rulerOn = vm.rulerVisible,
                            onRulerClick = { vm.toggleRuler() },
                            trailing = colorSwatchSlot
                        )
                    }
                }

                // ---- Portrait: horizontal tool bar sits right above the timeline ----
                if (!isLandscape) {
                    AnimatedVisibility(
                        visible = !isUiHidden,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ToolRail(
                            selectedTool = vm.tool,
                            onSelectTool = onSelectTool,
                            horizontal = true,
                            activeBrush = activeBrush,
                            onBrushClick = onBrushClick,
                            onToolDoubleTap = onToolDoubleTap,
                            activeShape = activeShape,
                            mirrorMode = vm.mirrorMode,
                            onMirrorClick = { vm.cycleMirror() },
                            rulerOn = vm.rulerVisible,
                            onRulerClick = { vm.toggleRuler() },
                            trailing = colorSwatchSlot
                        )
                    }
                }

                // ---- Bottom timeline ----
                AnimatedVisibility(
                    visible = !isUiHidden,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        StudioTimeline(
                            frames = vm.frames,
                            currentIndex = vm.currentIndex,
                            fps = vm.project?.fps ?: 12,
                            layers = vm.layerUi,
                            activeLayerIndex = vm.activeLayerIndex,
                            audioTracks = vm.audioTracks,
                            isPlaying = vm.isPlaying,
                            onionEnabled = vm.onionSkinSettings.enabled,
                            isStudioExpanded = isStudioExpanded,
                            onToggleStudioExpanded = { isStudioExpanded = !isStudioExpanded },
                            onSelectFrame = { vm.selectFrame(it) },
                            onTogglePlay = { vm.togglePlay() },
                            onPreviousFrame = { if (vm.currentIndex > 0) vm.selectFrame(vm.currentIndex - 1) },
                            onNextFrame = { if (vm.currentIndex < vm.frames.lastIndex) vm.selectFrame(vm.currentIndex + 1) },
                            onAddFrame = { vm.addFrame() },
                            onDuplicateFrame = { vm.duplicateFrame() },
                            onCopyFrame = { vm.copyFrame() },
                            onPasteFrame = { vm.pasteFrame() },
                            canPaste = vm.canPasteFrame,
                            onDeleteFrame = { showDeleteFrameDialog = true },
                            onToggleOnion = { vm.toggleOnionSkin() },
                            onSelectLayer = { vm.selectLayer(it) },
                            onToggleLayerVisibility = { idx, vis -> vm.setLayerVisible(idx, vis) },
                            onToggleLayerLock = { idx, locked -> vm.setLayerLocked(idx, locked) },
                            onAddLayer = { vm.addLayer() },
                            onFpsRequested = { showFpsDialog = true },
                            onAudioRequested = { showAudioDialog = true },
                            onUpdateAudioTrack = { vm.updateAudioTrack(it) },
                            onDeleteAudioTrack = { vm.deleteAudioTrack(it) },
                            onUpdateFrameExposure = { index, duration -> vm.updateFrameExposure(index, duration) }
                        )
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showColorPicker) {
        ColorPickerSheet(
            initialColor = vm.color,
            onPick = {
                vm.updateColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showDeleteFrameDialog) {
        WishyDialog(
            title = if (vm.frames.size <= 1) "Clear Frame?" else "Delete Frame?",
            onDismissRequest = { showDeleteFrameDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                vm.deleteFrame()
                showDeleteFrameDialog = false
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showDeleteFrameDialog = false }
        ) {
            Text(
                if (vm.frames.size <= 1) "This is the only frame, so it will be cleared (you can undo)."
                else "Frame ${vm.currentIndex + 1} will be deleted."
            )
        }
    }

    if (showFpsDialog) {
        var fpsVal by remember { mutableStateOf(vm.project?.fps?.toFloat() ?: 12f) }
        WishyDialog(
            title = "Project FPS",
            onDismissRequest = { showFpsDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                vm.updateProjectFps(fpsVal.roundToInt())
                showFpsDialog = false
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showFpsDialog = false }
        ) {
            WishySlider(
                value = fpsVal,
                onValueChange = { fpsVal = it },
                valueRange = 1f..60f,
                label = "Target FPS",
                unit = " fps"
            )
        }
    }

    if (showExportDialog) {
        WishyDialog(
            title = "Export Animation",
            onDismissRequest = { showExportDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showExportDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                TextButton(onClick = {
                    vm.project?.let { ExportService.start(context, it.id, ExportFormat.MP4) }
                    showExportDialog = false
                }) { Text("Export MP4 video") }
                TextButton(onClick = {
                    vm.project?.let { ExportService.start(context, it.id, ExportFormat.GIF) }
                    showExportDialog = false
                }) { Text("Export animated GIF") }
                TextButton(onClick = {
                    vm.project?.let { ExportService.start(context, it.id, ExportFormat.PNG_SEQUENCE) }
                    showExportDialog = false
                }) { Text("Export PNG sequence (ZIP)") }
            }
        }
    }

    if (showImportDialog) {
        WishyDialog(
            title = "Import Media",
            onDismissRequest = { showImportDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showImportDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                TextButton(onClick = {
                    imageSequencePicker.launch("image/*")
                    showImportDialog = false
                }) { Text("Import images as frames") }
                TextButton(onClick = {
                    videoPicker.launch("video/*")
                    showImportDialog = false
                }) { Text("Import video as frames") }
            }
        }
    }

    if (showOnionSkinDialog) {
        val s = vm.onionSkinSettings
        var onionOpacity by remember(s.opacity) { mutableStateOf(s.opacity * 100f) }
        WishyDialog(
            title = "Onion Skin",
            onDismissRequest = { showOnionSkinDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showOnionSkinDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                WishySlider(
                    value = s.framesBefore.toFloat(),
                    onValueChange = {
                        val v = it.roundToInt()
                        if (v != s.framesBefore) vm.updateOnionSkinSettings(s.copy(framesBefore = v))
                    },
                    valueRange = 0f..3f,
                    label = "Frames before"
                )
                WishySlider(
                    value = s.framesAfter.toFloat(),
                    onValueChange = {
                        val v = it.roundToInt()
                        if (v != s.framesAfter) vm.updateOnionSkinSettings(s.copy(framesAfter = v))
                    },
                    valueRange = 0f..3f,
                    label = "Frames after"
                )
                WishySlider(
                    value = onionOpacity,
                    onValueChange = { onionOpacity = it },
                    valueRange = 5f..80f,
                    label = "Opacity",
                    unit = "%",
                    onValueChangeFinished = {
                        vm.updateOnionSkinSettings(vm.onionSkinSettings.copy(opacity = onionOpacity / 100f))
                    }
                )
            }
        }
    }

    if (showAudioDialog) {
        WishyDialog(
            title = "Audio",
            onDismissRequest = { showAudioDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showAudioDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                if (vm.audioTracks.isEmpty()) {
                    Text(
                        "No audio yet. A track you add starts at the current frame.",
                        color = tokens.onSurfaceVariant
                    )
                }
                vm.audioTracks.forEach { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${track.name} - frame ${track.startFrame + 1}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        ActionIconButton(
                            iconRes = WishyIcons.Delete,
                            contentDescription = "Remove audio",
                            onClick = { vm.deleteAudioTrack(track.id) }
                        )
                    }
                }
                TextButton(onClick = {
                    audioPicker.launch("audio/*")
                    showAudioDialog = false
                }) { Text("Add audio file") }
            }
        }
    }

    if (vm.textEditorOpen) {
        var textValue by remember { mutableStateOf(vm.textEditorInitial) }
        WishyDialog(
            title = "Text Editor",
            onDismissRequest = { vm.dismissTextEditor() },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { vm.confirmText(textValue) },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { vm.dismissTextEditor() }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Type here") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Font Family", style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
                ) {
                    for (font in vm.availableFonts) {
                        val selected = font == vm.activeFontName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(if (selected) tokens.primaryContainer else tokens.surfaceVariant)
                                .clickable { vm.activeFontName = font }
                                .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs)
                        ) {
                            Text(
                                text = font,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) tokens.onPrimaryContainer else tokens.onSurface
                            )
                        }
                    }
                }
                TextButton(onClick = { fontPicker.launch("*/*") }) {
                    Text("+ Import Custom Font (.ttf / .otf)")
                }
            }
        }
    }

    if (showShortcutDialog) {
        ShortcutDialog(
            onDismissRequest = { showShortcutDialog = false }
        )
    }
}
