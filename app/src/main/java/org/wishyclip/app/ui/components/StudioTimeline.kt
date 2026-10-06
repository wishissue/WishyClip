package org.wishyclip.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wishyclip.app.canvas.LayerUi
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import java.util.Locale

/**
 * Full Studio Animation Timeline component.
 * Integrates playback controls, playhead indicator, multi-layer tracks, audio waveforms,
 * frame exposure duration indicators, and onion skin toggles.
 */
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
    onUpdateFrameExposure: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, frames.size) {
        if (frames.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, frames.lastIndex))
        }
    }

    val currentSec = if (fps > 0) currentIndex.toFloat() / fps else 0f
    val timecode = String.format(Locale.US, "%02d:%02d.%01d", (currentSec / 60).toInt(), (currentSec % 60).toInt(), ((currentSec * 10) % 10).toInt())

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tokens.timeline,
        shadowElevation = tokens.elevationMedium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ---- Top Header: Transport & Controls Bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Transport Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ActionIconButton(
                        iconRes = WishyIcons.SkipBack,
                        contentDescription = "Previous Frame",
                        onClick = onPreviousFrame
                    )
                    ActionIconButton(
                        iconRes = if (isPlaying) WishyIcons.Pause else WishyIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        selected = isPlaying,
                        onClick = onTogglePlay
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.SkipForward,
                        contentDescription = "Next Frame",
                        onClick = onNextFrame
                    )
                }

                Spacer(Modifier.width(tokens.spaceSmall))

                // Timecode & Frame Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.smallRadius))
                        .background(tokens.surfaceVariant)
                        .padding(horizontal = tokens.spaceSmall, vertical = 4.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1}/${frames.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = timecode,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(tokens.spaceSmall))

                // FPS Selector Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.smallRadius))
                        .background(tokens.surfaceVariant)
                        .clickable(onClick = onFpsRequested)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$fps fps",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.onSurface
                    )
                }

                Spacer(Modifier.weight(1f))

                // Onion Skin Toggle Action
                ActionIconButton(
                    iconRes = WishyIcons.Onion,
                    contentDescription = "Toggle Onion Skin",
                    selected = onionEnabled,
                    onClick = onToggleOnion
                )

                // Expand / Collapse Studio Multi-Track View Toggle
                ActionIconButton(
                    iconRes = WishyIcons.Layers,
                    contentDescription = "Studio Multi-Track View",
                    selected = isStudioExpanded,
                    onClick = onToggleStudioExpanded
                )

                ActionIconButton(
                    iconRes = WishyIcons.Duplicate,
                    contentDescription = "Duplicate frame",
                    onClick = onDuplicateFrame
                )

                ActionIconButton(
                    iconRes = WishyIcons.Delete,
                    contentDescription = "Delete frame",
                    onClick = onDeleteFrame
                )

                Box {
                    ActionIconButton(
                        iconRes = WishyIcons.More,
                        contentDescription = "More Frame Options",
                        onClick = { showMenu = true }
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy Frame") },
                            onClick = { showMenu = false; onCopyFrame() }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste Frame") },
                            enabled = canPaste,
                            onClick = { showMenu = false; onPasteFrame() }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio Track Manager") },
                            onClick = { showMenu = false; onAudioRequested() }
                        )
                    }
                }
            }

            // ---- Studio Multi-Track View / Single Row Strip ----
            AnimatedVisibility(visible = isStudioExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceXs)
                ) {
                    Text(
                        text = "STUDIO LAYERS & TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    layers.indices.reversed().forEach { idx ->
                        val layer = layers[idx]
                        val isSelected = idx == activeLayerIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(if (isSelected) tokens.primaryContainer.copy(alpha = 0.4f) else tokens.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSelectLayer(idx) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionIconButton(
                                iconRes = if (layer.visible) WishyIcons.VisibilityOn else WishyIcons.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                onClick = { onToggleLayerVisibility(idx, !layer.visible) }
                            )
                            ActionIconButton(
                                iconRes = WishyIcons.Lock,
                                contentDescription = "Toggle Lock",
                                selected = layer.locked,
                                onClick = { onToggleLayerLock(idx, !layer.locked) }
                            )
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) tokens.primary else tokens.onSurface,
                                modifier = Modifier.width(100.dp)
                            )
                            Text(
                                text = "Opacity: ${(layer.opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- Horizontally Scrolling Timeline Frame Track ----
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = tokens.spaceMedium,
                    end = tokens.spaceMedium,
                    bottom = tokens.spaceSmall,
                    top = tokens.spaceXs
                ),
                horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
            ) {
                itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
                    TimelineFrameCell(
                        frameIndex = index,
                        selected = index == currentIndex,
                        onClick = { onSelectFrame(index) },
                        exposureDuration = frame.exposureDuration,
                        onIncreaseExposure = { onUpdateFrameExposure(index, frame.exposureDuration + 1) },
                        onDecreaseExposure = if (frame.exposureDuration > 1) {
                            { onUpdateFrameExposure(index, frame.exposureDuration - 1) }
                        } else null
                    )
                }
                item(key = "add-frame") {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tokens.surfaceVariant)
                            .clickable { onAddFrame() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(WishyIcons.Add),
                            contentDescription = "Add frame",
                            tint = tokens.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
