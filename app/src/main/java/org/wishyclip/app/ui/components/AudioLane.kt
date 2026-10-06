package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import kotlin.math.roundToInt

/**
 * Compact audio lane for the timeline. The bar spans the whole project; each clip is a block
 * you drag left/right to set where it starts. Tap a clip to open volume, mute and remove.
 */
@Composable
fun AudioLane(
    tracks: List<AudioTrackEntity>,
    totalFrames: Int,
    fps: Int,
    currentFrame: Int,
    onUpdate: (AudioTrackEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val total = totalFrames.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = tokens.spaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Audio",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            ActionIconButton(iconRes = WishyIcons.Add, contentDescription = "Add audio", onClick = onAdd)
        }
        if (tracks.isEmpty()) {
            Text(
                text = "No audio. Tap + to add a track.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onSurfaceVariant
            )
        }
        tracks.forEach { track ->
            val selected = selectedId == track.id
            val clipFrames = ((track.durationMs - track.trimStartMs).coerceAtLeast(0L) * fps / 1000f)
                .roundToInt().coerceIn(1, total)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(tokens.smallRadius))
                    .background(tokens.surfaceVariant.copy(alpha = 0.6f))
            ) {
                val laneWidthPx = constraints.maxWidth.toFloat()
                val pxPerFrame = laneWidthPx / total
                val clipWidth: Dp = maxWidth * (clipFrames.toFloat() / total)
                var dragStart by remember(track.id, track.startFrame) { mutableStateOf(track.startFrame.toFloat()) }
                val startFrame = dragStart.coerceIn(0f, (total - clipFrames).coerceAtLeast(0).toFloat())

                // Playhead
                Box(
                    modifier = Modifier
                        .offset { IntOffset((currentFrame * pxPerFrame).roundToInt(), 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(tokens.primary.copy(alpha = 0.8f))
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset((startFrame * pxPerFrame).roundToInt(), 0) }
                        .width(clipWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(tokens.smallRadius))
                        .background(if (selected) tokens.primary else tokens.primaryContainer)
                        .clickable { selectedId = if (selected) null else track.id }
                        .pointerInput(track.id, total, clipFrames) {
                            detectDragGestures(
                                onDragEnd = {
                                    onUpdate(track.copy(startFrame = dragStart.roundToInt().coerceIn(0, (total - clipFrames).coerceAtLeast(0))))
                                }
                            ) { change, amount ->
                                change.consume()
                                dragStart += amount.x / pxPerFrame
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) tokens.onPrimary else tokens.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
                ) {
                    ActionIconButton(
                        iconRes = WishyIcons.Audio,
                        contentDescription = if (track.volume > 0f) "Mute" else "Unmute",
                        selected = track.volume <= 0f,
                        onClick = { onUpdate(track.copy(volume = if (track.volume > 0f) 0f else 1f)) }
                    )
                    var vol by remember(track.id, track.volume) { mutableStateOf(track.volume * 100f) }
                    WishySlider(
                        value = vol,
                        onValueChange = { vol = it },
                        onValueChangeFinished = { onUpdate(track.copy(volume = vol / 100f)) },
                        valueRange = 0f..100f,
                        label = "Volume",
                        unit = "%",
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        iconRes = WishyIcons.Delete,
                        contentDescription = "Remove audio",
                        onClick = { selectedId = null; onDelete(track.id) }
                    )
                }
            }
        }
    }
}
