package org.wishyclip.app.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onExit: () -> Unit,
    isLeftHanded: Boolean = false,
    palmRejection: Boolean = false,
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
    var renameLayerIndex by remember { mutableStateOf<Int?>(null) }
    // (isMerge, layer index): delete/merge now change EVERY frame, so they are confirmed first.
    var layerConfirm by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
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
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.importFonts(uris)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val runExport: (ExportFormat, Int) -> Unit = { format, frameIndex ->
        // Flush unsaved strokes first: the export service renders from the saved files.
        vm.saveThen { vm.project?.let { ExportService.start(context, it.id, format, frameIndex) } }
    }
    var pendingExport by remember { mutableStateOf<Pair<ExportFormat, Int>?>(null) }
    // Back closes the open panel/menu first; only with nothing open does it leave the editor.
    // (Registered after the exit handler above, so it wins while enabled. Dialogs handle Back themselves.)
    BackHandler(enabled = showColorPicker || showBrushMenu || showToolOptions || showLayersPanel || showOverflowMenu) {
        when {
            showColorPicker -> showColorPicker = false
            showBrushMenu -> showBrushMenu = false
            showToolOptions -> showToolOptions = false
            showLayersPanel -> showLayersPanel = false
            showOverflowMenu -> showOverflowMenu = false
        }
    }
    // Android 8-9 have no scoped storage: saving into Movies/Pictures needs this permission.
    // If it is refused the export still runs; the service then keeps the file in the app's storage.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingExport?.let { (format, frameIndex) -> runExport(format, frameIndex) }
        pendingExport = null
    }
    /** Starts an export; on Android 13+ also asks once for permission to show its progress notification. */
    val startExport: (ExportFormat, Int) -> Unit = { format, frameIndex ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingExport = Pair(format, frameIndex)
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runExport(format, frameIndex)
        }
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
    // Hardware-keyboard shortcuts (the list shown in "Keyboard Shortcuts"). Uses onKeyEvent, not the
    // preview variant, so a focused button still gets Space/Enter before this handler sees them.
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    val handleKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = handleKey@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handleKey false
        val firstPress = event.nativeKeyEvent.repeatCount == 0
        val mod = event.isCtrlPressed || event.isMetaPressed
        when {
            mod && event.key == Key.Z -> { if (event.isShiftPressed) vm.redo() else vm.undo(); true }
            mod && event.key == Key.Y -> { vm.redo(); true }
            mod -> false
            event.key == Key.DirectionLeft -> {
                if (vm.currentIndex > 0) vm.selectFrame(vm.currentIndex - 1)
                true
            }
            event.key == Key.DirectionRight -> {
                if (vm.currentIndex < vm.frames.lastIndex) vm.selectFrame(vm.currentIndex + 1)
                true
            }
            !firstPress -> false
            event.key == Key.Spacebar -> { vm.togglePlay(); true }
            event.key == Key.B -> { onSelectTool(activeBrush); true }
            event.key == Key.E -> { onSelectTool(Tool.ERASER); true }
            event.key == Key.F -> { onSelectTool(Tool.FILL); true }
            event.key == Key.L -> { onSelectTool(Tool.LASSO); true }
            event.key == Key.T -> { onSelectTool(Tool.TEXT); true }
            event.key == Key.R -> { vm.toggleRuler(); true }
            event.key == Key.M -> { vm.cycleMirror(); true }
            event.key == Key.O -> { vm.toggleOnionSkin(); true }
            else -> false
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
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent(handleKey)
            .focusRequester(keyFocus)
            .focusable(),
        color = tokens.canvasBackdrop
    ) {
        if (vm.loadError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui_could_not_open_this_project), color = tokens.onSurface)
                    TextButton(onClick = onExit) { Text(stringResource(R.string.ui_back)) }
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
                            contentDescription = stringResource(R.string.ui_back),
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
                            contentDescription = stringResource(R.string.ui_layers),
                            selected = showLayersPanel,
                            onClick = { showLayersPanel = !showLayersPanel }
                        )
                        Box {
                            ActionIconButton(
                                iconRes = WishyIcons.More,
                                contentDescription = stringResource(R.string.ui_more_options),
                                onClick = { showOverflowMenu = true }
                            )
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_export_animation)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_import_media_2)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_onion_skin_settings)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showOnionSkinDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_hide_interface)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showBrushMenu = false
                                        showToolOptions = false
                                        showLayersPanel = false
                                        isUiHidden = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_rotate_canvas_90)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        canvasView.rotateBy(90f)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_reset_view)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        canvasView.reset()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_keyboard_shortcuts)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showShortcutDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_import_brushes_2)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        brushPicker.launch("*/*")
                                    }
                                )
                                if (onOpenDesignGallery != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_design_gallery_debug)) },
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
                                palmRejection = palmRejection,
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
                                    contentDescription = stringResource(R.string.ui_show_interface),
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
                                onEditText = { vm.editActiveText() },
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
                                dabFor = { vm.customDab(it) },
                                onUpdateCustom = { vm.updateCustomBrush(it) },
                                onCommitCustom = { vm.commitCustomBrush() },
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
                                            label = stringResource(R.string.ui_tolerance)
                                        )
                                    } else {
                                        WishySlider(
                                            value = vm.brushSize,
                                            onValueChange = { vm.updateBrushSize(it) },
                                            valueRange = 1f..60f,
                                            label = stringResource(R.string.ui_size),
                                            unit = "px",
                                            onValueChangeFinished = { vm.persistBrush() }
                                        )
                                    }
                                    WishySlider(
                                        value = vm.opacity * 100f,
                                        onValueChange = { vm.updateOpacity(it / 100f) },
                                        valueRange = 5f..100f,
                                        label = stringResource(R.string.ui_opacity),
                                        unit = "%",
                                        onValueChangeFinished = { vm.persistBrush() }
                                    )
                                    WishySlider(
                                        value = vm.stabilizer * 100f,
                                        onValueChange = { vm.updateStabilizer(it / 100f) },
                                        valueRange = 0f..100f,
                                        label = stringResource(R.string.ui_stabilizer),
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
                                onDeleteLayer = { idx -> layerConfirm = Pair(false, idx) },
                                onToggleLock = { idx, locked -> vm.setLayerLocked(idx, locked) },
                                onCycleBlend = { idx -> vm.layerUi.getOrNull(idx)?.let { vm.setLayerBlend(idx, it.blendMode.next()) } },
                                onDuplicateLayer = { idx -> vm.duplicateLayer(idx) },
                                onMergeDown = { idx -> layerConfirm = Pair(true, idx) },
                                onRenameLayer = { idx -> renameLayerIndex = idx },
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

    renameLayerIndex?.let { idx ->
        var text by remember(idx) { mutableStateOf(vm.layerUi.getOrNull(idx)?.name ?: "") }
        WishyDialog(
            title = stringResource(R.string.ui_rename_layer),
            onDismissRequest = { renameLayerIndex = null },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                vm.renameLayer(idx, text)
                renameLayerIndex = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { renameLayerIndex = null }
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true
            )
        }
    }

    layerConfirm?.let { (isMerge, idx) ->
        val layerName = vm.layerUi.getOrNull(idx)?.name ?: ""
        WishyDialog(
            title = if (isMerge) "Merge layer down?" else "Delete layer?",
            onDismissRequest = { layerConfirm = null },
            confirmText = if (isMerge) "Merge" else "Delete",
            onConfirm = {
                if (isMerge) vm.mergeLayerDown(idx) else vm.deleteLayer(idx)
                layerConfirm = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { layerConfirm = null }
        ) {
            Text(
                if (isMerge) "\"$layerName\" will be flattened into the layer below on every frame. This can't be undone."
                else "\"$layerName\" and its drawings will be removed from every frame. This can't be undone."
            )
        }
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
            title = stringResource(R.string.ui_project_fps),
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
                label = stringResource(R.string.ui_target_fps),
                unit = " fps"
            )
        }
    }

    if (showExportDialog) {
        WishyDialog(
            title = stringResource(R.string.ui_export_animation),
            onDismissRequest = { showExportDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showExportDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                TextButton(onClick = {
                    startExport(ExportFormat.MP4, -1)
                    showExportDialog = false
                }) { Text(stringResource(R.string.ui_export_mp4_video)) }
                TextButton(onClick = {
                    startExport(ExportFormat.GIF, -1)
                    showExportDialog = false
                }) { Text(stringResource(R.string.ui_export_animated_gif)) }
                TextButton(onClick = {
                    startExport(ExportFormat.PNG_SEQUENCE, -1)
                    showExportDialog = false
                }) { Text(stringResource(R.string.ui_export_png_sequence_zip)) }
                TextButton(onClick = {
                    startExport(ExportFormat.PNG_CURRENT_FRAME, vm.currentIndex)
                    showExportDialog = false
                }) { Text(stringResource(R.string.ui_export_current_frame_png)) }
                Text(
                    stringResource(R.string.ui_video_is_exported_without_audio_for_no),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant
                )
            }
        }
    }

    if (showImportDialog) {
        WishyDialog(
            title = stringResource(R.string.ui_import_media_2),
            onDismissRequest = { showImportDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showImportDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                Text(stringResource(R.string.ui_into_the_animation), style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                TextButton(onClick = {
                    imageSequencePicker.launch("image/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_images_as_new_frames)) }
                TextButton(onClick = {
                    videoPicker.launch("video/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_video_as_frames)) }
                TextButton(onClick = {
                    imageLayerPicker.launch("image/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_image_as_a_new_layer_on_this_frame)) }
                TextButton(onClick = {
                    audioPicker.launch("audio/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_audio_track)) }
                Text(stringResource(R.string.ui_customize), style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                TextButton(onClick = {
                    brushPicker.launch("*/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_brushes_png_abr_gbr_kpp_procreate)) }
                TextButton(onClick = {
                    fontPicker.launch("*/*")
                    showImportDialog = false
                }) { Text(stringResource(R.string.ui_fonts_ttf_otf)) }
            }
        }
    }

    if (showOnionSkinDialog) {
        val s = vm.onionSkinSettings
        var onionOpacity by remember(s.opacity) { mutableStateOf(s.opacity * 100f) }
        WishyDialog(
            title = stringResource(R.string.ui_onion_skin_2),
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
                    label = stringResource(R.string.ui_frames_before)
                )
                WishySlider(
                    value = s.framesAfter.toFloat(),
                    onValueChange = {
                        val v = it.roundToInt()
                        if (v != s.framesAfter) vm.updateOnionSkinSettings(s.copy(framesAfter = v))
                    },
                    valueRange = 0f..3f,
                    label = stringResource(R.string.ui_frames_after)
                )
                WishySlider(
                    value = onionOpacity,
                    onValueChange = { onionOpacity = it },
                    valueRange = 5f..80f,
                    label = stringResource(R.string.ui_opacity),
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
            title = stringResource(R.string.ui_audio),
            onDismissRequest = { showAudioDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showAudioDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                if (vm.audioTracks.isEmpty()) {
                    Text(
                        stringResource(R.string.ui_no_audio_yet_a_track_you_add_starts_at),
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
                            contentDescription = stringResource(R.string.ui_remove_audio),
                            onClick = { vm.deleteAudioTrack(track.id) }
                        )
                    }
                }
                TextButton(onClick = {
                    audioPicker.launch("audio/*")
                    showAudioDialog = false
                }) { Text(stringResource(R.string.ui_add_audio_file)) }
            }
        }
    }

    if (vm.textEditorOpen) {
        var textValue by remember { mutableStateOf(vm.textEditorInitial) }
        val activeFamily = remember(vm.activeFontName, vm.availableFonts) {
            vm.fontTypeface(vm.activeFontName)?.let { FontFamily(it) }
        }
        WishyDialog(
            title = stringResource(R.string.ui_text_editor),
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
                    label = { Text(stringResource(R.string.ui_type_here)) },
                    // Shows the text in the chosen font, so the preview matches what gets drawn.
                    textStyle = TextStyle(
                        fontFamily = activeFamily,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.ui_font), style = MaterialTheme.typography.labelMedium, color = tokens.onSurfaceVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
                ) {
                    for (font in vm.availableFonts) {
                        val selected = font.key == vm.activeFontName
                        val family = remember(font.key) { vm.fontTypeface(font.key)?.let { FontFamily(it) } }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(if (selected) tokens.primaryContainer else tokens.surfaceVariant)
                                .clickable { vm.activeFontName = font.key }
                                .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = font.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = family,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) tokens.onPrimaryContainer else tokens.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (!font.builtIn) {
                                ActionIconButton(
                                    iconRes = WishyIcons.Delete,
                                    contentDescription = "Remove font ${font.label}",
                                    onClick = { vm.deleteFont(font.key) }
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { fontPicker.launch("*/*") }) {
                    Text(stringResource(R.string.ui_import_fonts_ttf_otf))
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
