package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.FrameEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

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
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, frames.size) {
        if (frames.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, frames.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.timeline)
            .padding(vertical = tokens.spaceXs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconButton(iconRes = WishyIcons.Add, contentDescription = "Add Frame", onClick = onAddFrame)
            ActionIconButton(iconRes = WishyIcons.Duplicate, contentDescription = "Duplicate", onClick = onDuplicateFrame)
            TextButton(onClick = onCopyFrame) { Text("Copy", style = MaterialTheme.typography.labelMedium) }
            TextButton(enabled = canPaste, onClick = onPasteFrame) { Text("Paste", style = MaterialTheme.typography.labelMedium) }
            ActionIconButton(iconRes = WishyIcons.Delete, contentDescription = "Delete", onClick = onDeleteRequested)
            TextButton(onClick = onAudioRequested) { Text("🎵 Audio", style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = onImportRequested) { Text("📁 Import", style = MaterialTheme.typography.labelMedium) }
            Text(
                text = "${currentIndex + 1}/${frames.size} @ ${fps}fps",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = tokens.spaceSmall)
                    .clickable { onFpsRequested() }
            )
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spaceXs),
            contentPadding = PaddingValues(horizontal = tokens.spaceSmall),
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
        ) {
            itemsIndexed(frames, key = { _, f -> f.id }) { index, _ ->
                TimelineFrameCell(
                    frameIndex = index,
                    selected = index == currentIndex,
                    onClick = { onSelectFrame(index) }
                )
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
            onFpsRequested = {}
        )
    }
}
