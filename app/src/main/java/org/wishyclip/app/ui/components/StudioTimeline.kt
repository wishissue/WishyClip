package org.wishyclip.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import java.io.File
import java.util.Locale
import org.wishyclip.app.R
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme

/**
 * The Material 3 animation timeline, in FlipaClip's two layouts.
 *
 * Classic (collapsed): one row of transport + counter + actions, and the frame strip with real
 * thumbnails. Studio ([isStudioExpanded]): adds the layer list and the audio lane above the strip.
 * Tap a frame to select it, long-press for its menu, drag the right edge of the selected frame to
 * hold it longer.
 *
 * [frameThumb] maps a frame id to its thumbnail file; [thumbVersion] busts the image cache when a
 * frame is redrawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTimeline(
    frames: List<FrameEntity>,
    currentIndex: Int,
    fps: Int,
    layers: List<LayerUi>,
    activeLayerIndex: Int,
    audioTracks: List<AudioTrackEntity>,
    isPlaying: Boolean,
    onionEnabled: Boolean,
    isStudioExpanded: Boolean,
    onToggleStudioExpanded: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onPreviousFrame: () -> Unit,
    onNextFrame: () -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onCopyFrame: () -> Unit,
    onPasteFrame: () -> Unit,
    canPaste: Boolean,
    onDeleteFrame: () -> Unit,
    onToggleOnion: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onToggleLayerVisibility: (Int, Boolean) -> Unit,
    onToggleLayerLock: (Int, Boolean) -> Unit,
    onAddLayer: () -> Unit,
    onFpsRequested: () -> Unit,
    onAudioRequested: () -> Unit,
    onUpdateAudioTrack: (AudioTrackEntity) -> Unit = {},
    onDeleteAudioTrack: (Long) -> Unit = {},
    onUpdateFrameExposure: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    frameThumb: ((Long) -> File)? = null,
    thumbVersion: Int = 0,
    onOpenLayers: (() -> Unit)? = null,
    /** Moves the selected frame one place earlier (-1) or later (+1). */
    onMoveFrame: (Int) -> Unit = {},
    /** Drag-reorder: move the frame at the first index to the second. */
    onMoveFrameTo: (Int, Int) -> Unit = { _, _ -> },
    /** Frame id -> indices of layers that have a drawing on that frame (Studio tracks). */
    layerContent: Map<Long, Set<Int>> = emptyMap()
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = WishyTheme.tokens
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    var menuFrame by remember { mutableStateOf(-1) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    fun cellPx(i: Int): Float {
        val hold = (frames.getOrNull(i)?.exposureDuration ?: 1).coerceAtLeast(1)
        return with(density) { (tokens.frameCellWidth + 24.dp * (hold - 1).coerceIn(0, 6)).toPx() } + gapPx
    }
    // Where a dragged frame lands: it passes a neighbour once it is over half of that neighbour.
    fun dropIndex(from: Int, dx: Float): Int {
        var t = from
        var used = 0f
        if (dx > 0) {
            while (t < frames.lastIndex && dx - used > cellPx(t + 1) / 2f) { used += cellPx(t + 1); t++ }
        } else {
            while (t > 0 && -dx - used > cellPx(t - 1) / 2f) { used += cellPx(t - 1); t-- }
        }
        return t
    }

    LaunchedEffect(currentIndex, frames.size) {
        if (frames.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, frames.lastIndex))
        }
    }

    // Time counts held frames (exposure), so the timecode matches what playback and export produce.
    val slotsBefore = frames.take(currentIndex.coerceAtLeast(0)).sumOf { it.exposureDuration.coerceAtLeast(1) }
    val currentSec = if (fps > 0) slotsBefore.toFloat() / fps else 0f
    val timecode = String.format(
        Locale.US, "%02d:%02d.%01d",
        (currentSec / 60).toInt(), (currentSec % 60).toInt(), ((currentSec * 10) % 10).toInt()
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
        ),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // ---- Header: transport, counter, actions ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onPreviousFrame) {
                        Icon(painterResource(WishyIcons.SkipBack), stringResource(R.string.ui_previous_frame))
                    }
                    PrimaryRoundButton(
                        iconRes = if (isPlaying) WishyIcons.Pause else WishyIcons.Play,
                        contentDescription = stringResource(if (isPlaying) R.string.ui_pause else R.string.ui_play),
                        onClick = onTogglePlay
                    )
                    IconButton(onClick = onNextFrame) {
                        Icon(painterResource(WishyIcons.SkipForward), stringResource(R.string.ui_next_frame))
                    }
                    Spacer(Modifier.width(4.dp))
                    AssistChip(
                        onClick = onFpsRequested,
                        label = { Text("${currentIndex + 1}/${frames.size} · $fps fps · $timecode") }
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onOpenLayers != null) {
                        ActionIconButton(
                            iconRes = WishyIcons.Layers,
                            contentDescription = stringResource(R.string.ui_layers),
                            onClick = onOpenLayers
                        )
                    }
                    ActionIconButton(
                        iconRes = WishyIcons.Onion,
                        contentDescription = stringResource(R.string.ui_toggle_onion_skin),
                        selected = onionEnabled,
                        onClick = onToggleOnion
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.Reorder,
                        contentDescription = stringResource(R.string.ui_studio_multi_track_view),
                        selected = isStudioExpanded,
                        onClick = onToggleStudioExpanded
                    )
                    Box {
                        ActionIconButton(
                            iconRes = WishyIcons.More,
                            contentDescription = stringResource(R.string.ui_more_frame_options),
                            onClick = { menuFrame = currentIndex; showMenu = true }
                        )
                        FrameMenu(
                            expanded = showMenu,
                            canPaste = canPaste,
                            onDismiss = { showMenu = false },
                            onDuplicate = onDuplicateFrame,
                            onCopy = onCopyFrame,
                            onPaste = onPasteFrame,
                            onDelete = onDeleteFrame,
                            onAudio = onAudioRequested,
                            canMoveLeft = currentIndex > 0,
                            canMoveRight = currentIndex < frames.lastIndex,
                            onMove = onMoveFrame
                        )
                    }
                }
            }

            // ---- Studio: layer list and audio lane ----
            AnimatedVisibility(visible = isStudioExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ui_layers_tracks),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onAddLayer) {
                            Icon(painterResource(WishyIcons.Add), stringResource(R.string.ui_add_layer))
                        }
                    }
                    layers.indices.reversed().forEach { idx ->
                        val layer = layers[idx]
                        val isSelected = idx == activeLayerIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
                                .clickable { onSelectLayer(idx) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleLayerVisibility(idx, !layer.visible) }) {
                                Icon(
                                    painterResource(if (layer.visible) WishyIcons.VisibilityOn else WishyIcons.VisibilityOff),
                                    stringResource(R.string.ui_toggle_visibility)
                                )
                            }
                            IconButton(onClick = { onToggleLayerLock(idx, !layer.locked) }) {
                                Icon(
                                    painterResource(WishyIcons.Lock),
                                    stringResource(R.string.ui_toggle_lock),
                                    tint = if (layer.locked) scheme.primary else scheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.width(72.dp)
                            )
                            // One track per layer: a block per frame (as wide as its hold), filled
                            // where this layer has a drawing; the playhead frame is outlined.
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                frames.forEachIndexed { fi, f ->
                                    val filled = layerContent[f.id]?.contains(idx) == true
                                    Box(
                                        modifier = Modifier
                                            .weight(f.exposureDuration.coerceAtLeast(1).toFloat())
                                            .fillMaxHeight()
                                            .padding(vertical = 4.dp)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(
                                                if (filled) scheme.primary.copy(alpha = if (fi == currentIndex) 1f else 0.55f)
                                                else scheme.surfaceContainerHighest
                                            )
                                            .then(
                                                if (fi == currentIndex) Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.extraSmall)
                                                else Modifier
                                            )
                                            .clickable { onSelectFrame(fi) }
                                    )
                                }
                            }
                        }
                    }
                    AudioLane(
                        tracks = audioTracks,
                        totalFrames = frames.sumOf { it.exposureDuration.coerceAtLeast(1) },
                        fps = fps,
                        currentFrame = slotsBefore,
                        onUpdate = onUpdateAudioTrack,
                        onDelete = onDeleteAudioTrack,
                        onAdd = onAudioRequested
                    )
                }
            }

            // ---- Frame strip ----
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
                    val file = frameThumb?.invoke(frame.id)
                    val model: Any? = if (file != null && file.exists()) {
                        ImageRequest.Builder(context)
                            .data(file)
                            .memoryCacheKey("frame-${frame.id}-$thumbVersion")
                            .build()
                    } else null
                    val dragging = dragIdx == index
                    Box(
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (dragging) dragDx else 0f
                                alpha = if (dragging) 0.85f else 1f
                            }
                            .pointerInput(frame.id, index, frames.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragIdx = index
                                        dragDx = 0f
                                        onSelectFrame(index)
                                    },
                                    onDragEnd = {
                                        val to = dropIndex(index, dragDx)
                                        dragIdx = -1
                                        dragDx = 0f
                                        if (to != index) onMoveFrameTo(index, to)
                                    },
                                    onDragCancel = {
                                        dragIdx = -1
                                        dragDx = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDx += amount.x
                                    }
                                )
                            }
                    ) {
                        TimelineFrameCell(
                            frameIndex = index,
                            selected = index == currentIndex,
                            onClick = { onSelectFrame(index) },
                            exposureDuration = frame.exposureDuration,
                            thumbnail = model,
                            onExposureChange = { onUpdateFrameExposure(index, it) }
                        )
                    }
                }
                item(key = "add-frame") {
                    Box(
                        modifier = Modifier
                            .size(width = tokens.frameCellWidth, height = tokens.frameCellHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(scheme.primaryContainer)
                            .clickable(onClick = onAddFrame),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(WishyIcons.Add),
                            contentDescription = stringResource(R.string.ui_add_frame),
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameMenu(
    expanded: Boolean,
    canPaste: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onAudio: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_duplicate_frame)) },
            onClick = { onDismiss(); onDuplicate() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_copy_frame)) },
            onClick = { onDismiss(); onCopy() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_paste_frame)) },
            enabled = canPaste,
            onClick = { onDismiss(); onPaste() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_move_frame_left)) },
            enabled = canMoveLeft,
            onClick = { onDismiss(); onMove(-1) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_move_frame_right)) },
            enabled = canMoveRight,
            onClick = { onDismiss(); onMove(1) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_audio_track_manager)) },
            onClick = { onDismiss(); onAudio() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_delete_frame), color = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}
