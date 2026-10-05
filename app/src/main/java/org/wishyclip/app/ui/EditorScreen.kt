package org.wishyclip.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.audio.VoiceRecorder
import org.wishyclip.app.audio.WaveformExtractor
import org.wishyclip.app.canvas.DrawingCanvas
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.export.ExportFormat
import org.wishyclip.app.export.ExportService
import org.wishyclip.app.model.Tool
import java.io.File

@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
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

    var showLayers by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showDeleteFrame by remember { mutableStateOf(false) }
    var showOnionSkinDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var textInputPosition by remember { mutableStateOf(Offset.Zero) }

    fun comingSoon(feature: String) {
        Toast.makeText(context, "Coming soon: $feature", Toast.LENGTH_SHORT).show()
    }

    Surface(Modifier.fillMaxSize()) {
        if (vm.loadError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not open this project.")
                    TextButton(onClick = onExit) { Text("Back") }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ---- top bar ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBtn(R.drawable.ic_back, "Back") { vm.saveAndExit(onExit) }
                    Text(
                        vm.project?.name ?: "",
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconBtn(R.drawable.ic_undo, "Undo", enabled = vm.canUndo) { vm.undo() }
                    IconBtn(R.drawable.ic_redo, "Redo", enabled = vm.canRedo) { vm.redo() }
                    IconBtn(
                        if (vm.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        if (vm.isPlaying) "Pause" else "Play"
                    ) { vm.togglePlay() }
                    IconBtn(
                        R.drawable.ic_onion,
                        "Onion skin",
                        selected = vm.onionSkinSettings.enabled,
                        onLongClick = { showOnionSkinDialog = true },
                        onClick = { vm.toggleOnionSkin() }
                    )
                    IconBtn(R.drawable.ic_export, "Export") { showExportDialog = true }
                }

                // ---- canvas ----
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
                        onStrokeStart = { x, y, p, t ->
                            if (vm.tool == Tool.TEXT) {
                                textInputPosition = Offset(x, y)
                                showTextInputDialog = true
                            } else {
                                vm.strokeStart(x, y, p, t)
                            }
                        },
                        onStrokeMove = { x, y, p, t -> vm.strokeMove(x, y, p, t) },
                        onStrokeEnd = { vm.strokeEnd() },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // ---- tools ----
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item { IconBtn(R.drawable.ic_pen, "Pen", selected = vm.tool == Tool.PEN) { vm.selectTool(Tool.PEN) } }
                    item { IconBtn(R.drawable.ic_pencil, "Pencil", selected = vm.tool == Tool.PENCIL) { vm.selectTool(Tool.PENCIL) } }
                    item { IconBtn(R.drawable.ic_marker, "Marker", selected = vm.tool == Tool.MARKER) { vm.selectTool(Tool.MARKER) } }
                    item { IconBtn(R.drawable.ic_eraser, "Eraser", selected = vm.tool == Tool.ERASER) { vm.selectTool(Tool.ERASER) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.AIRBRUSH) }) { Text("Airbrush", color = if (vm.tool == Tool.AIRBRUSH) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.CALLIGRAPHY) }) { Text("Chisel", color = if (vm.tool == Tool.CALLIGRAPHY) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.HIGHLIGHTER) }) { Text("Highlight", color = if (vm.tool == Tool.HIGHLIGHTER) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { IconBtn(R.drawable.ic_fill, "Fill", selected = vm.tool == Tool.FILL) { vm.selectTool(Tool.FILL) } }
                    item { IconBtn(R.drawable.ic_lasso, "Lasso", selected = vm.tool == Tool.LASSO) { vm.selectTool(Tool.LASSO) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.LINE) }) { Text("Line", color = if (vm.tool == Tool.LINE) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.RECT) }) { Text("Rect", color = if (vm.tool == Tool.RECT) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.ELLIPSE) }) { Text("Oval", color = if (vm.tool == Tool.ELLIPSE) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item { TextButton(onClick = { vm.selectTool(Tool.TEXT) }) { Text("Text", color = if (vm.tool == Tool.TEXT) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
                    item {
                        Box(
                            Modifier.padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(vm.color))
                                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { showColor = true }
                            )
                        }
                    }
                    item { IconBtn(R.drawable.ic_layers, "Layers") { showLayers = true } }
                }

                if (vm.activeLassoSelection != null) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lasso:", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { vm.moveLassoSelection(-15f, 0f) }) { Text("←") }
                        TextButton(onClick = { vm.moveLassoSelection(15f, 0f) }) { Text("→") }
                        TextButton(onClick = { vm.moveLassoSelection(0f, -15f) }) { Text("↑") }
                        TextButton(onClick = { vm.moveLassoSelection(0f, 15f) }) { Text("↓") }
                        TextButton(onClick = { vm.scaleLassoSelection(1.1f, 1.1f) }) { Text("+") }
                        TextButton(onClick = { vm.scaleLassoSelection(0.9f, 0.9f) }) { Text("-") }
                        TextButton(onClick = { vm.rotateLassoSelection(15f) }) { Text("⟳") }
                        TextButton(onClick = { vm.commitLassoSelection() }) { Text("✔") }
                        TextButton(onClick = { vm.deleteLassoSelection() }) { Text("🗑") }
                    }
                }

                // ---- size / opacity / stabilizer ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vm.tool == Tool.FILL) {
                        Text("Tol ${vm.fillTolerance}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                        Slider(
                            value = vm.fillTolerance.toFloat(),
                            onValueChange = { vm.updateFillTolerance(it.roundToInt()) },
                            valueRange = 0f..128f,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text("Size ${vm.brushSize.roundToInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                        Slider(
                            value = vm.brushSize,
                            onValueChange = { vm.updateBrushSize(it) },
                            onValueChangeFinished = { vm.persistBrush() },
                            valueRange = 1f..60f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Op ${(vm.opacity * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                    Slider(
                        value = vm.opacity,
                        onValueChange = { vm.updateOpacity(it) },
                        onValueChangeFinished = { vm.persistBrush() },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Smooth ${(vm.stabilizer * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                    Slider(
                        value = vm.stabilizer,
                        onValueChange = { vm.updateStabilizer(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ---- timeline ----
                Timeline(
                    vm = vm,
                    onDeleteRequested = { showDeleteFrame = true },
                    onAudioRequested = { showAudioDialog = true },
                    onImportRequested = { showImportMenu = true },
                    onFpsRequested = { showFpsDialog = true }
                )
            }
        }
    }

    if (showLayers) LayersDialog(vm = vm, onDismiss = { showLayers = false })
    if (showOnionSkinDialog) OnionSkinDialog(vm = vm, onDismiss = { showOnionSkinDialog = false })
    if (showAudioDialog) AudioDialog(vm = vm, onDismiss = { showAudioDialog = false })

    if (showFpsDialog) {
        var fpsValue by remember { mutableFloatStateOf(vm.project?.fps?.toFloat() ?: 12f) }
        AlertDialog(
            onDismissRequest = { showFpsDialog = false },
            title = { Text("Project FPS") },
            text = {
                Column {
                    Text("FPS: ${fpsValue.roundToInt()}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = fpsValue,
                        onValueChange = { fpsValue = it },
                        valueRange = 1f..60f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateProjectFps(fpsValue.roundToInt())
                    showFpsDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFpsDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Animation") },
            text = {
                Column {
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
                    TextButton(onClick = {
                        vm.project?.let { ExportService.start(context, it.id, ExportFormat.PNG_CURRENT_FRAME) }
                        showExportDialog = false
                    }) { Text("🖼 Export Current Frame PNG") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportMenu) {
        val imageSequencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                vm.importImageSequence(uris)
                showImportMenu = false
            }
        }
        val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                vm.importVideoAsFrames(uri)
                showImportMenu = false
            }
        }
        AlertDialog(
            onDismissRequest = { showImportMenu = false },
            title = { Text("Import Media") },
            text = {
                Column {
                    TextButton(onClick = { imageSequencePicker.launch("image/*") }) {
                        Text("🖼 Import Images as Frames")
                    }
                    TextButton(onClick = { videoPicker.launch("video/*") }) {
                        Text("🎬 Import Video as Frames")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportMenu = false }) { Text("Close") }
            }
        )
    }

    if (showTextInputDialog) {
        var textValue by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Add Text") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Enter text") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textValue.isNotBlank()) {
                        vm.drawTextAt(textValue, textInputPosition.x, textInputPosition.y)
                    }
                    showTextInputDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showColor) {
        ColorPickerDialog(
            initial = vm.color,
            onPick = {
                vm.updateColor(it)
                showColor = false
            },
            onDismiss = { showColor = false }
        )
    }

    if (showDeleteFrame) {
        AlertDialog(
            onDismissRequest = { showDeleteFrame = false },
            title = { Text(if (vm.frames.size <= 1) "Clear frame?" else "Delete frame?") },
            text = {
                Text(
                    if (vm.frames.size <= 1) "This is the only frame, so it will be cleared (you can undo)."
                    else "Frame ${vm.currentIndex + 1} will be permanently deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFrame()
                    showDeleteFrame = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDeleteFrame = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconBtn(
    icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(36.dp),
            alpha = if (enabled) 1f else 0.35f
        )
    }
}

@Composable
private fun OnionSkinDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val s = vm.onionSkinSettings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onion Skin Settings") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = s.enabled,
                        onCheckedChange = { vm.updateOnionSkinSettings(s.copy(enabled = it)) }
                    )
                    Text("Enable Onion Skin")
                }
                Text("Frames Before: ${s.framesBefore}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = s.framesBefore.toFloat(),
                    onValueChange = { vm.updateOnionSkinSettings(s.copy(framesBefore = it.roundToInt())) },
                    valueRange = 0f..3f,
                    steps = 2
                )
                Text("Frames After: ${s.framesAfter}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = s.framesAfter.toFloat(),
                    onValueChange = { vm.updateOnionSkinSettings(s.copy(framesAfter = it.roundToInt())) },
                    valueRange = 0f..3f,
                    steps = 2
                )
                Text("Opacity: ${(s.opacity * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = s.opacity,
                    onValueChange = { vm.updateOnionSkinSettings(s.copy(opacity = it)) },
                    valueRange = 0.1f..0.8f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun Timeline(
    vm: EditorViewModel,
    onDeleteRequested: () -> Unit,
    onAudioRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onFpsRequested: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.currentIndex, vm.frames.size) {
        if (vm.frames.isNotEmpty()) {
            listState.animateScrollToItem(vm.currentIndex.coerceIn(0, vm.frames.size - 1))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_add, "Add frame") { vm.addFrame() }
            IconBtn(R.drawable.ic_copy, "Duplicate frame") { vm.duplicateFrame() }
            TextButton(onClick = { vm.copyFrame() }) { Text("Copy") }
            TextButton(enabled = vm.canPasteFrame, onClick = { vm.pasteFrame() }) { Text("Paste") }
            IconBtn(R.drawable.ic_delete, "Delete frame") { onDeleteRequested() }
            TextButton(onClick = onAudioRequested) { Text("🎵 Audio") }
            TextButton(onClick = onImportRequested) { Text("📁 Import") }
            Text(
                "<",
                modifier = Modifier.clickable { vm.moveFrame(-1) }.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                ">",
                modifier = Modifier.clickable { vm.moveFrame(1) }.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "${vm.currentIndex + 1}/${vm.frames.size} @ ${vm.project?.fps ?: 0}fps",
                modifier = Modifier.weight(1f).clickable { onFpsRequested() },
                style = MaterialTheme.typography.labelSmall
            )
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(vm.frames, key = { _, f -> f.id }) { index, _ ->
                val selected = index == vm.currentIndex
                Box(
                    Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { vm.selectFrame(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        for (track in vm.audioTracks) {
            WaveformStrip(track = track)
        }
    }
}

@Composable
private fun WaveformStrip(track: AudioTrackEntity) {
    val samples = remember(track.filePath) {
        WaveformExtractor.extractWaveform(File(track.filePath), samplesCount = 60)
    }
    Row(
        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp, vertical = 2.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎵 ${track.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp))
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val barWidth = size.width / samples.size.coerceAtLeast(1)
            val centerY = size.height / 2f
            for (i in samples.indices) {
                val h = (samples[i] * size.height / 2f).coerceAtLeast(2f)
                drawLine(
                    color = Color.Blue,
                    start = Offset(i * barWidth + barWidth / 2f, centerY - h),
                    end = Offset(i * barWidth + barWidth / 2f, centerY + h),
                    strokeWidth = (barWidth * 0.6f).coerceAtLeast(2f)
                )
            }
        }
    }
}

@Composable
private fun AudioDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<VoiceRecorder?>(null) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.addAudioTrackFromUri(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Tracks") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { audioPicker.launch("audio/*") }) {
                        Text("+ Import Track")
                    }
                    TextButton(onClick = {
                        if (!isRecording) {
                            val recorder = VoiceRecorder(context)
                            val audioDir = File(context.filesDir, "projects/${vm.project?.id}/audio")
                            val file = recorder.startRecording(audioDir)
                            if (file != null) {
                                voiceRecorder = recorder
                                isRecording = true
                            }
                        } else {
                            val file = voiceRecorder?.stopRecording()
                            isRecording = false
                            voiceRecorder = null
                            if (file != null && file.exists()) {
                                vm.addRecordedVoiceTrack(file)
                            }
                        }
                    }) {
                        Text(if (isRecording) "🔴 Stop Recording" else "🎙 Record Voice")
                    }
                }
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(vm.audioTracks, key = { it.id }) { track ->
                        Column(
                            Modifier.fillMaxWidth().padding(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(track.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Text("x", modifier = Modifier.clickable { vm.deleteAudioTrack(track.id) }.padding(4.dp))
                            }
                            Text("Start Frame: ${track.startFrame}", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = track.startFrame.toFloat(),
                                onValueChange = { vm.updateAudioTrack(track.copy(startFrame = it.roundToInt())) },
                                valueRange = 0f..(vm.frames.size.toFloat().coerceAtLeast(1f))
                            )
                            Text("Volume: ${(track.volume * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = track.volume,
                                onValueChange = { vm.updateAudioTrack(track.copy(volume = it)) },
                                valueRange = 0f..1f
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun LayersDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val imageLayerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.importImageAsLayer(uri)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layers (this frame)") },
        text = {
            Column {
                val ui = vm.layerUi
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(ui.indices.reversed().toList()) { idx ->
                        val layer = ui[idx]
                        val active = idx == vm.activeLayerIndex
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.selectLayer(idx) }
                                .padding(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = layer.visible, onCheckedChange = { vm.setLayerVisible(idx, it) })
                                Text(layer.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("^", modifier = Modifier.clickable { vm.moveLayer(idx, 1) }.padding(8.dp))
                                Text("v", modifier = Modifier.clickable { vm.moveLayer(idx, -1) }.padding(8.dp))
                                Text("x", modifier = Modifier.clickable { vm.deleteLayer(idx) }.padding(8.dp))
                            }
                            if (active) {
                                Slider(
                                    value = layer.opacity,
                                    onValueChange = { vm.setLayerOpacity(idx, it) },
                                    valueRange = 0f..1f
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.addLayer() }) { Text("+ Add layer") }
                    TextButton(onClick = { imageLayerPicker.launch("image/*") }) { Text("🖼 Import Image") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
