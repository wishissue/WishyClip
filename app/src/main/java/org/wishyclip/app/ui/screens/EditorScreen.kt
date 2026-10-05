package org.wishyclip.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import org.wishyclip.app.canvas.DrawingCanvas
import org.wishyclip.app.export.ExportFormat
import org.wishyclip.app.export.ExportService
import org.wishyclip.app.model.Tool
import org.wishyclip.app.ui.EditorViewModel
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ColorPickerSheet
import org.wishyclip.app.ui.components.ColorSwatch
import org.wishyclip.app.ui.components.LayerPanel
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

    var isUiHidden by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showToolOptions by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteFrameDialog by remember { mutableStateOf(false) }
    var showOnionSkinDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var textInputPosition by remember { mutableStateOf(Offset.Zero) }

    val imageLayerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importImageAsLayer(uri)
    }
    val imageSequencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) vm.importImageSequence(uris)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importVideoAsFrames(uri)
    }

    WishyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.surface
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
                    // ---- Top Bar ----
                    AnimatedVisibility(
                        visible = !isUiHidden,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(tokens.topBarHeight)
                                .background(tokens.surface)
                                .padding(horizontal = tokens.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionIconButton(
                                iconRes = WishyIcons.Back,
                                contentDescription = "Back",
                                onClick = { vm.saveAndExit(onExit) }
                            )
                            Text(
                                text = vm.project?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = tokens.spaceSmall)
                                    .clickable {
                                        renameText = vm.project?.name ?: ""
                                        showRenameDialog = true
                                    }
                            )
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
                                iconRes = if (vm.isPlaying) WishyIcons.Pause else WishyIcons.Play,
                                contentDescription = if (vm.isPlaying) "Pause" else "Play",
                                onClick = { vm.togglePlay() }
                            )
                            ActionIconButton(
                                iconRes = WishyIcons.Onion,
                                contentDescription = "Onion Skin",
                                selected = vm.onionSkinSettings.enabled,
                                onClick = { vm.toggleOnionSkin() }
                            )
                            ActionIconButton(
                                iconRes = WishyIcons.Layers,
                                contentDescription = "Layers",
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

                    // ---- Main Workspace (Rail + Canvas + Side Panel) ----
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left Tool Rail (or Right if Left-Handed)
                        if (!isLeftHanded && !isUiHidden) {
                            ToolRail(
                                selectedTool = vm.tool,
                                onSelectTool = { selected ->
                                    if (vm.tool == selected) {
                                        showToolOptions = !showToolOptions
                                    } else {
                                        vm.selectTool(selected)
                                        showToolOptions = true
                                    }
                                }
                            )
                        }

                        // Canvas Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(tokens.canvasBackdrop)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            // One-tap to toggle focus UI mode
                                            isUiHidden = !isUiHidden
                                        },
                                        onDoubleTap = {
                                            // Two-finger / double-tap quick undo
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
                                    revision = { vm.revision },
                                    enabled = !vm.isPlaying,
                                    onStrokeStart = { x, y, press, tilt ->
                                        if (vm.tool == Tool.TEXT) {
                                            textInputPosition = Offset(x, y)
                                            showTextInputDialog = true
                                        } else {
                                            vm.strokeStart(x, y, press, tilt)
                                        }
                                    },
                                    onStrokeMove = { x, y, press, tilt -> vm.strokeMove(x, y, press, tilt) },
                                    onStrokeEnd = { vm.strokeEnd() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }

                            // Tool options anchored popup sheet
                            if (showToolOptions && !isUiHidden) {
                                Surface(
                                    modifier = Modifier
                                        .align(if (isLeftHanded) Alignment.TopEnd else Alignment.TopStart)
                                        .padding(tokens.spaceSmall)
                                        .width(220.dp),
                                    shape = RoundedCornerShape(tokens.mediumRadius),
                                    color = tokens.surface,
                                    shadowElevation = tokens.elevationMedium
                                ) {
                                    Column(
                                        modifier = Modifier.padding(tokens.spaceSmall),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                                    ) {
                                        Text(
                                            text = "${vm.tool.name} Options",
                                            style = MaterialTheme.typography.titleSmall,
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

                                        // Live brush stroke preview
                                        Text(
                                            text = "Brush Preview",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.onSurfaceVariant
                                        )
                                        val brushColor = Color(vm.color)
                                        val bSize = vm.brushSize
                                        val bOp = vm.opacity
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(tokens.smallRadius))
                                                .background(tokens.surfaceVariant)
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

                            // Floating Color Swatch (Bottom Corner)
                            if (!isUiHidden) {
                                ColorSwatch(
                                    color = Color(vm.color),
                                    onClick = { showColorPicker = true },
                                    modifier = Modifier
                                        .align(if (isLeftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                                        .padding(tokens.spaceLarge)
                                )
                            }
                        }

                        // Right Tool Rail (if Left-Handed)
                        if (isLeftHanded && !isUiHidden) {
                            ToolRail(
                                selectedTool = vm.tool,
                                onSelectTool = { selected ->
                                    if (vm.tool == selected) {
                                        showToolOptions = !showToolOptions
                                    } else {
                                        vm.selectTool(selected)
                                        showToolOptions = true
                                    }
                                }
                            )
                        }

                        // Layers Side Sheet
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
                                modifier = Modifier.width(260.dp)
                            )
                        }
                    }

                    // ---- Bottom Timeline Strip ----
                    AnimatedVisibility(
                        visible = !isUiHidden,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TimelineStrip(
                            frames = vm.frames,
                            currentIndex = vm.currentIndex,
                            fps = vm.project?.fps ?: 12,
                            audioTracks = vm.audioTracks,
                            onSelectFrame = { vm.selectFrame(it) },
                            onAddFrame = { vm.addFrame() },
                            onDuplicateFrame = { vm.duplicateFrame() },
                            onCopyFrame = { vm.copyFrame() },
                            onPasteFrame = { vm.pasteFrame() },
                            canPaste = vm.canPasteFrame,
                            onDeleteRequested = { showDeleteFrameDialog = true },
                            onAudioRequested = { showAudioDialog = true },
                            onImportRequested = { showImportDialog = true },
                            onFpsRequested = { showFpsDialog = true }
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
                }) { Text("🎬 Export MP4 Video") }
                TextButton(onClick = {
                    vm.project?.let { ExportService.start(context, it.id, ExportFormat.GIF) }
                    showExportDialog = false
                }) { Text("🎞 Export Animated GIF") }
                TextButton(onClick = {
                    vm.project?.let { ExportService.start(context, it.id, ExportFormat.PNG_SEQUENCE) }
                    showExportDialog = false
                }) { Text("📦 Export PNG Sequence (ZIP)") }
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
                }) { Text("🖼 Import Images as Frames") }
                TextButton(onClick = {
                    videoPicker.launch("video/*")
                    showImportDialog = false
                }) { Text("🎬 Import Video as Frames") }
            }
        }
    }

    if (showTextInputDialog) {
        var textValue by remember { mutableStateOf("") }
        WishyDialog(
            title = "Add Text",
            onDismissRequest = { showTextInputDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                if (textValue.isNotBlank()) {
                    vm.drawTextAt(textValue, textInputPosition.x, textInputPosition.y)
                }
                showTextInputDialog = false
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showTextInputDialog = false }
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("Enter text") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
