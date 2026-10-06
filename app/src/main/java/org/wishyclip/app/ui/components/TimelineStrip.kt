package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * Bottom timeline: a slim control row (frame counter, fps, onion skin, duplicate, delete, more)
 * above a horizontally scrolling strip of frames that ends with a "+" cell.
 */
@Composable
fun TimelineStrip(
    frames: List<FrameEntity>,
    currentIndex: Int,
    fps: Int,
    audioTracks: List<AudioTrackEntity>,
    onSelectFrame: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onCopyFrame: () -> Unit,
    onPasteFrame: () -> Unit,
    canPaste: Boolean,
    onDeleteRequested: () -> Unit,
    onAudioRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onFpsRequested: () -> Unit,
    modifier: Modifier = Modifier,
    onionEnabled: Boolean = false,
    onToggleOnion: (() -> Unit)? = null,
    onUpdateFrameExposure: ((Int, Int) -> Unit)? = null
) {
    val tokens = WishyTheme.tokens
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, frames.size) {
        if (frames.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, frames.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.timeline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = tokens.spaceMedium, end = tokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentIndex + 1} / ${frames.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.onSurface
            )
            Spacer(Modifier.width(tokens.spaceSmall))
            Text(
                text = "$fps fps",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.smallRadius))
                    .background(tokens.surfaceVariant)
                    .clickable { onFpsRequested() }
                    .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceXs)
            )
            if (audioTracks.isNotEmpty()) {
                Spacer(Modifier.width(tokens.spaceSmall))
                Icon(
                    painter = painterResource(WishyIcons.Audio),
                    contentDescription = "Audio tracks: ${audioTracks.size}",
                    tint = tokens.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${audioTracks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (onToggleOnion != null) {
                ActionIconButton(
                    iconRes = WishyIcons.Onion,
                    contentDescription = "Onion skin",
                    selected = onionEnabled,
                    onClick = onToggleOnion
                )
            }
            ActionIconButton(iconRes = WishyIcons.Duplicate, contentDescription = "Duplicate frame", onClick = onDuplicateFrame)
            ActionIconButton(iconRes = WishyIcons.Delete, contentDescription = "Delete frame", onClick = onDeleteRequested)
            Box {
                ActionIconButton(iconRes = WishyIcons.More, contentDescription = "More frame actions", onClick = { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy frame") },
                        onClick = { showMenu = false; onCopyFrame() }
                    )
                    DropdownMenuItem(
                        text = { Text("Paste frame") },
                        enabled = canPaste,
                        onClick = { showMenu = false; onPasteFrame() }
                    )
                    DropdownMenuItem(
                        text = { Text("Audio") },
                        onClick = { showMenu = false; onAudioRequested() }
                    )
                    DropdownMenuItem(
                        text = { Text("Import media") },
                        onClick = { showMenu = false; onImportRequested() }
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = tokens.spaceMedium, end = tokens.spaceMedium, bottom = tokens.spaceSmall, top = tokens.spaceXs),
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
        ) {
            itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
                TimelineFrameCell(
                    frameIndex = index,
                    selected = index == currentIndex,
                    onClick = { onSelectFrame(index) },
                    exposureDuration = frame.exposureDuration,
                    onIncreaseExposure = if (onUpdateFrameExposure != null) { { onUpdateFrameExposure(index, frame.exposureDuration + 1) } } else null,
                    onDecreaseExposure = if (onUpdateFrameExposure != null && frame.exposureDuration > 1) { { onUpdateFrameExposure(index, frame.exposureDuration - 1) } } else null
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

@Preview(name = "TimelineStrip Light")
@Composable
private fun TimelineStripLightPreview() {
    WishyTheme(tokens = LightTokens) {
        TimelineStrip(
            frames = listOf(FrameEntity(id = 1, projectId = 1, position = 0), FrameEntity(id = 2, projectId = 1, position = 1)),
            currentIndex = 0,
            fps = 12,
            audioTracks = emptyList(),
            onSelectFrame = {},
            onAddFrame = {},
            onDuplicateFrame = {},
            onCopyFrame = {},
            onPasteFrame = {},
            canPaste = true,
            onDeleteRequested = {},
            onAudioRequested = {},
            onImportRequested = {},
            onFpsRequested = {}
        )
    }
}

@Preview(name = "TimelineStrip Dark")
@Composable
private fun TimelineStripDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        TimelineStrip(
            frames = listOf(FrameEntity(id = 1, projectId = 1, position = 0)),
            currentIndex = 0,
            fps = 24,
            audioTracks = emptyList(),
            onSelectFrame = {},
            onAddFrame = {},
            onDuplicateFrame = {},
            onCopyFrame = {},
            onPasteFrame = {},
            canPaste = false,
            onDeleteRequested = {},
            onAudioRequested = {},
            onImportRequested = {},
            onFpsRequested = {},
            onionEnabled = true,
            onToggleOnion = {}
        )
    }
}
