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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
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
import org.wishyclip.app.ui.components.TimelineMode
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
    defaultTimelineMode: TimelineMode = TimelineMode.COMPACT,
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
    // Timeline size: the drag handle on its top edge steps through these.
    var timelineMode by rememberSaveable { mutableStateOf(defaultTimelineMode) }
    val isStudioExpanded = timelineMode == TimelineMode.EXPANDED

    // Layer previews for the layers panel / Studio view; rebuilt (debounced) only while one is visible.
    var layerThumbs by remember { mutableStateOf<Map<Long, ImageBitmap>>(emptyMap()) }
    val thumbsWanted = showLayersPanel || isStudioExpanded
    LaunchedEffect(thumbsWanted, vm.revision, vm.currentIndex, vm.layerUi.size) {
        if (!thumbsWanted) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        layerThumbs = vm.currentLayers().mapNotNull { layer ->
            layer.peekBitmap()?.let { layer.id to scaledLayerThumb(it) }
        }.toMap()
    }
    // Which frames have a drawing on which layer (Studio per-layer tracks).
    var layerContent by remember { mutableStateOf<Map<Long, Set<Int>>>(emptyMap()) }
    LaunchedEffect(isStudioExpanded, vm.frameThumbVersion, vm.frames.size, vm.layerUi.size) {
        if (isStudioExpanded) layerContent = vm.layerContentMap()
    }
    // Frames saved before per-frame thumbnails existed get theirs generated in the background.
    LaunchedEffect(vm.frames.size) { vm.backfillFrameThumbs() }

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
    val showTitle = LocalConfiguration.current.screenWidthDp >= 600
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
                  // Flat 48dp bar on the stage colour (no capsule, no margins): Back, title, Undo, Redo, Layers, More.
                  Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
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

                        // Floating tool rail over the stage (never takes canvas space); flips for left-handed use
                        if (!isUiHidden) {
                            ToolRail(
                                selectedTool = vm.tool,
                                onSelectTool = onSelectTool,
                                maxHeight = maxHeight - tokens.floatMargin * 2,
                                activeBrush = activeBrush,
                                onBrushClick = onBrushClick,
                                onToolDoubleTap = onToolDoubleTap,
                                activeShape = activeShape,
                                mirrorMode = vm.mirrorMode,
                                onMirrorClick = { vm.cycleMirror() },
                                rulerOn = vm.rulerVisible,
                                onRulerClick = { vm.toggleRuler() },
                                trailing = colorSwatchSlot,
                                modifier = Modifier
                                    .align(if (isLeftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                                    .padding(tokens.floatMargin)
                            )
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
                                onFitLasso = { vm.fitLassoSelectionToCanvas(cover = false) },
                                onFillLasso = { vm.fitLassoSelectionToCanvas(cover = true) },
                                onRotateLasso = { vm.rotateLassoSelection(90f) },
                                onResetProportions = { vm.resetLassoProportions() },
                                onOpenToolOptions = {
                                    if (vm.tool.isBrush) showBrushMenu = true else showToolOptions = true
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = tokens.spaceSmall)
                            )
                        }

                        // Floating Brush Selector Panel (collapsible popover over the canvas)
                        if (!isUiHidden && showBrushMenu) {
                            GlassSurface(
                                modifier = Modifier
                                    .align(if (isLeftHanded) Alignment.TopEnd else Alignment.TopStart)
                                    .padding(tokens.spaceSmall)
                                    .padding(
                                        start = if (isLandscape && !isLeftHanded) 64.dp else 0.dp,
                                        end = if (isLandscape && isLeftHanded) 64.dp else 0.dp
                                    )
                                    .then(
                                        if (isLandscape) Modifier.width(300.dp)
                                        else Modifier.fillMaxWidth().widthIn(max = 380.dp)
                                    )
                            ) {
                                BrushMenu(
                                    selectedBrush = activeBrush,
                                    onSelectBrush = { selected ->
                                        activeBrush = selected
                                        onSelectTool(selected)
                                        showBrushMenu = false
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
                                    onSelectCustom = { id ->
                                        vm.selectCustomBrush(id)
                                        showBrushMenu = false
                                    },
                                    onImportBrushes = { brushPicker.launch("*/*") },
                                    dabFor = { vm.customDab(it) },
                                    onUpdateCustom = { vm.updateCustomBrush(it) },
                                    onCommitCustom = { vm.commitCustomBrush() },
                                    onDeleteCustom = { vm.deleteCustomBrush(it) },
                                    modifier = Modifier.padding(tokens.spaceSmall)
                                )
                            }
                        }

                        // Floating Tool Options Panel for size, opacity, fill tolerance
                        if (!isUiHidden && showToolOptions) {
                            GlassSurface(
                                modifier = Modifier
                                    .align(if (isLeftHanded) Alignment.TopEnd else Alignment.TopStart)
                                    .padding(tokens.spaceSmall)
                                    .padding(
                                        start = if (isLandscape && !isLeftHanded) 64.dp else 0.dp,
                                        end = if (isLandscape && isLeftHanded) 64.dp else 0.dp
                                    )
                                    .then(
                                        if (isLandscape) Modifier.width(240.dp)
                                        else Modifier.fillMaxWidth().widthIn(max = 380.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(tokens.spaceMedium),
                                    verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                                ) {
                                    Text(
                                        text = "${vm.tool.displayName} Options",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.onSurface
                                    )
                                    if (vm.tool.isBrush || vm.tool == Tool.ERASER ||
                                        vm.tool == Tool.LINE || vm.tool == Tool.RECT || vm.tool == Tool.ELLIPSE
                                    ) {
                                        WishySlider(
                                            value = vm.brushSize,
                                            onValueChange = { vm.updateBrushSize(it) },
                                            valueRange = 1f..(if (vm.tool == Tool.CUSTOM) 200f else 100f),
                                            label = stringResource(R.string.ui_brush_size),
                                            unit = "px"
                                        )
                                    }
                                    if (vm.tool.isBrush || vm.tool == Tool.TEXT) {
                                        WishySlider(
                                            value = vm.opacity * 100f,
                                            onValueChange = { vm.updateOpacity(it / 100f) },
                                            valueRange = 5f..100f,
                                            label = stringResource(R.string.ui_opacity),
                                            unit = "%"
                                        )
                                    }
                                    if (vm.tool.isBrush) {
                                        WishySlider(
                                            value = vm.stabilizer * 100f,
                                            onValueChange = { vm.updateStabilizer(it / 100f) },
                                            valueRange = 0f..100f,
                                            label = stringResource(R.string.ui_stabilizer),
                                            unit = "%"
                                        )
                                    }
                                    if (vm.tool == Tool.FILL) {
                                        WishySlider(
                                            value = vm.fillTolerance.toFloat(),
                                            onValueChange = { vm.updateFillTolerance(it.roundToInt()) },
                                            valueRange = 0f..255f,
                                            label = stringResource(R.string.ui_tolerance),
                                            unit = ""
                                        )
                                    }
                                    TextButton(
                                        onClick = { showToolOptions = false },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(stringResource(R.string.ui_close))
                                    }
                                }
                            }
                        }

                        // Floating Layers Panel (collapsible popover over the canvas)
                        if (!isUiHidden && showLayersPanel) {
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
                                onSetBlend = { idx, blend -> vm.setLayerBlend(idx, blend) },
                                onDuplicateLayer = { idx -> vm.duplicateLayer(idx) },
                                onMergeDown = { idx -> layerConfirm = Pair(true, idx) },
                                onRenameLayer = { idx -> renameLayerIndex = idx },
                                maxListHeight = (maxHeight - 200.dp).coerceIn(80.dp, 280.dp),
                                thumbnails = layerThumbs,
                                modifier = Modifier
                                    .align(if (isLeftHanded) Alignment.TopStart else Alignment.TopEnd)
                                    .padding(tokens.spaceSmall)
                                    .then(
                                        if (isLandscape) Modifier.width(300.dp)
                                        else Modifier.fillMaxWidth().widthIn(max = 380.dp)
                                    )
                            )
                        }

                        // Status pill (e.g. "This layer is locked").
                        vm.message?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = tokens.spaceLarge)
                                    .clip(RoundedCornerShape(50))
                                    .background(tokens.primary)
                                    .padding(horizontal = tokens.spaceLarge, vertical = tokens.spaceSmall)
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ---- Bottom timeline ----
                AnimatedVisibility(
                    visible = !isUiHidden,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = tokens.spaceXs)
                    ) {
                        StudioTimeline(
                            frames = vm.frames,
                            currentIndex = vm.currentIndex,
                            fps = vm.project?.fps ?: 12,
                            layers = vm.layerUi,
                            activeLayerIndex = vm.activeLayerIndex,
                            audioTracks = vm.audioTracks,
                            isPlaying = vm.isPlaying,
                            onionEnabled = vm.onionSkinSettings.enabled,
                            mode = timelineMode,
                            onModeChange = { timelineMode = it },
                            landscape = isLandscape,
                            // the layer tracks scroll inside a cap so EXPANDED can never swallow the canvas
                            maxExpandedHeight = (LocalConfiguration.current.screenHeightDp * (if (isLandscape) 0.30f else 0.28f)).dp,
                            onSelectFrame = { vm.selectFrame(it) },
                            onTogglePlay = { vm.togglePlay() },
                            onPreviousFrame = {
                                if (vm.currentIndex > 0) vm.selectFrame(vm.currentIndex - 1)
                            },
                            onNextFrame = {
                                if (vm.currentIndex < vm.frames.lastIndex) vm.selectFrame(vm.currentIndex + 1)
                            },
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
                            onUpdateFrameExposure = { index, duration -> vm.updateFrameExposure(index, duration) },
                            frameThumb = { id -> vm.frameThumbFile(id) },
                            thumbVersion = vm.frameThumbVersion,
                            onMoveFrame = { vm.moveFrame(it) },
                            onMoveFrameTo = { from, to -> vm.moveFrameTo(from, to) },
                            layerContent = layerContent
                        )
                    }
                }
            }
        }
    }

    if (showDeleteFrameDialog) {
        val total = vm.frames.size
        WishyDialog(
            title = stringResource(R.string.ui_delete_frame_2),
            onDismissRequest = { showDeleteFrameDialog = false },
            confirmText = stringResource(R.string.ui_delete),
            onConfirm = {
                vm.deleteFrame()
                showDeleteFrameDialog = false
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { showDeleteFrameDialog = false }
        ) {
            Text(
                if (total <= 1) "This is the only frame: deleting it will clear all layers on this frame."
                else "Frame ${vm.currentIndex + 1} of $total will be permanently deleted."
            )
        }
    }

    layerConfirm?.let { (isMerge, idx) ->
        val layerName = vm.layerUi.getOrNull(idx)?.name ?: "Layer"
        val title = if (isMerge) "Merge \"$layerName\" down?" else "Delete \"$layerName\"?"
        val detail = if (isMerge)
            "\"$layerName\" will be merged into the layer below on EVERY frame. This can't be undone."
        else
            "\"$layerName\" will be permanently deleted from EVERY frame in the animation. This can't be undone."
        WishyDialog(
            title = title,
            onDismissRequest = { layerConfirm = null },
            confirmText = if (isMerge) "Merge" else "Delete",
            onConfirm = {
                if (isMerge) vm.mergeLayerDown(idx) else vm.deleteLayer(idx)
                layerConfirm = null
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { layerConfirm = null }
        ) {
            Text(detail)
        }
    }

    renameLayerIndex?.let { idx ->
        val layer = vm.layerUi.getOrNull(idx)
        if (layer != null) {
            var text by remember(idx) { mutableStateOf(layer.name) }
            WishyDialog(
                title = stringResource(R.string.ui_rename_layer),
                onDismissRequest = { renameLayerIndex = null },
                confirmText = stringResource(R.string.action_save),
                onConfirm = {
                    vm.renameLayer(idx, text)
                    renameLayerIndex = null
                },
                dismissText = stringResource(R.string.ui_cancel),
                onDismiss = { renameLayerIndex = null }
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Layer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showFpsDialog) {
        val currentFps = vm.project?.fps ?: 12
        var pickedFps by remember { mutableStateOf(currentFps) }
        WishyDialog(
            title = stringResource(R.string.ui_project_fps),
            onDismissRequest = { showFpsDialog = false },
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                vm.updateProjectFps(pickedFps)
                showFpsDialog = false
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { showFpsDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                WishySlider(
                    value = pickedFps.toFloat(),
                    onValueChange = { pickedFps = it.roundToInt() },
                    valueRange = 1f..60f,
                    label = stringResource(R.string.ui_target_fps),
                    unit = " fps"
                )
                Text(
                    text = "Common frame rates: 12 fps (classic animation), 24 fps (film), 30 / 60 fps (smooth video).",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant
                )
            }
        }
    }

    if (showOnionSkinDialog) {
        val current = vm.onionSkinSettings
        var before by remember { mutableStateOf(current.framesBefore) }
        var after by remember { mutableStateOf(current.framesAfter) }
        var opacity by remember { mutableStateOf(current.opacity) }
        WishyDialog(
            title = stringResource(R.string.ui_onion_skin_2),
            onDismissRequest = { showOnionSkinDialog = false },
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                vm.updateOnionSkinSettings(
                    current.copy(
                        enabled = before > 0 || after > 0,
                        framesBefore = before,
                        framesAfter = after,
                        opacity = opacity
                    )
                )
                showOnionSkinDialog = false
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { showOnionSkinDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                WishySlider(
                    value = before.toFloat(),
                    onValueChange = { before = it.roundToInt() },
                    valueRange = 0f..5f,
                    label = stringResource(R.string.ui_frames_before),
                    unit = ""
                )
                WishySlider(
                    value = after.toFloat(),
                    onValueChange = { after = it.roundToInt() },
                    valueRange = 0f..5f,
                    label = stringResource(R.string.ui_frames_after),
                    unit = ""
                )
                WishySlider(
                    value = opacity * 100f,
                    onValueChange = { opacity = it / 100f },
                    valueRange = 10f..90f,
                    label = stringResource(R.string.ui_opacity),
                    unit = "%"
                )
            }
        }
    }

    if (vm.textEditorOpen) {
        var text by remember(vm.textEditorInitial) { mutableStateOf(vm.textEditorInitial) }
        WishyDialog(
            title = stringResource(R.string.ui_text_editor),
            onDismissRequest = { vm.dismissTextEditor() },
            confirmText = stringResource(R.string.ui_done),
            onConfirm = { vm.confirmText(text) },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { vm.dismissTextEditor() }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.ui_font),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.onSurface
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (opt in vm.availableFonts) {
                        TextButton(
                            onClick = { vm.activeFontName = opt.key },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = opt.label,
                                style = TextStyle(
                                    fontFamily = vm.fontTypeface(opt.key)?.let { FontFamily(it) }
                                ),
                                color = if (vm.activeFontName == opt.key) tokens.primary else tokens.onSurface
                            )
                        }
                    }
                    TextButton(onClick = { fontPicker.launch("*/*") }) {
                        Text(stringResource(R.string.ui_import_fonts_ttf_otf))
                    }
                }
            }
        }
    }

    if (showAudioDialog) {
        val tracks = vm.audioTracks
        WishyDialog(
            title = stringResource(R.string.ui_audio_track_manager),
            onDismissRequest = { showAudioDialog = false },
            confirmText = stringResource(R.string.ui_close),
            onConfirm = { showAudioDialog = false },
            dismissText = null,
            onDismiss = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                if (tracks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ui_no_audio_yet_a_track_you_add_starts_at),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.onSurfaceVariant
                    )
                } else {
                    for (track in tracks) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.onSurface
                                )
                                Text(
                                    text = "Starts at frame ${track.startFrame + 1} • ${(track.durationMs / 1000f).roundToInt()}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.deleteAudioTrack(track.id) }) {
                                Text(stringResource(R.string.ui_delete), color = tokens.danger)
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { audioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_add_audio_file))
                }
            }
        }
    }

    if (showImportDialog) {
        WishyDialog(
            title = stringResource(R.string.ui_import_media_2),
            onDismissRequest = { showImportDialog = false },
            confirmText = stringResource(R.string.ui_close),
            onConfirm = { showImportDialog = false },
            dismissText = null,
            onDismiss = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                Text(
                    text = stringResource(R.string.ui_into_the_animation),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        showImportDialog = false
                        imageSequencePicker.launch("image/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_images_as_new_frames))
                }
                TextButton(
                    onClick = {
                        showImportDialog = false
                        videoPicker.launch("video/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_video_as_frames))
                }
                Text(
                    text = stringResource(R.string.ui_image_as_a_new_layer_on_this_frame),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceSmall)
                )
                TextButton(
                    onClick = {
                        showImportDialog = false
                        imageLayerPicker.launch("image/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Image as a new layer")
                }
                Text(
                    text = stringResource(R.string.ui_audio_track),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceSmall)
                )
                TextButton(
                    onClick = {
                        showImportDialog = false
                        audioPicker.launch("audio/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_add_audio_file))
                }
                Text(
                    text = stringResource(R.string.ui_customize),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceSmall)
                )
                TextButton(
                    onClick = {
                        showImportDialog = false
                        brushPicker.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_brushes_png_abr_gbr_kpp_procreate))
                }
                TextButton(
                    onClick = {
                        showImportDialog = false
                        fontPicker.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_fonts_ttf_otf))
                }
            }
        }
    }

    if (showExportDialog) {
        WishyDialog(
            title = stringResource(R.string.ui_export_animation),
            onDismissRequest = { showExportDialog = false },
            confirmText = stringResource(R.string.ui_close),
            onConfirm = { showExportDialog = false },
            dismissText = null,
            onDismiss = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        startExport(ExportFormat.MP4, vm.currentIndex)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_export_mp4_video))
                }
                TextButton(
                    onClick = {
                        showExportDialog = false
                        startExport(ExportFormat.GIF, vm.currentIndex)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_export_animated_gif))
                }
                TextButton(
                    onClick = {
                        showExportDialog = false
                        startExport(ExportFormat.PNG_SEQUENCE, vm.currentIndex)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_export_png_sequence_zip))
                }
                TextButton(
                    onClick = {
                        showExportDialog = false
                        startExport(ExportFormat.PNG_CURRENT_FRAME, vm.currentIndex)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_export_current_frame_png))
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            initialColor = vm.color,
            onPick = { vm.updateColor(it) },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showShortcutDialog) {
        ShortcutDialog(onDismissRequest = { showShortcutDialog = false })
    }
}

/** A small preview (longest side ~128px) of a layer's pixels for the layer list. */
private fun scaledLayerThumb(source: Bitmap): ImageBitmap {
    val scale = 128f / maxOf(source.width, source.height).coerceAtLeast(1)
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, w, h, true).asImageBitmap()
}
