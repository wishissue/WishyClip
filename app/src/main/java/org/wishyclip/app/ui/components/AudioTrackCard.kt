package org.wishyclip.app.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishyclip.app.R
import org.wishyclip.app.audio.WaveformExtractor
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme

/**
 * One audio track in the audio popup: name, where it starts, length and file size, a waveform
 * preview, and play / delete buttons. [playing] drives the play/stop icon; the caller owns the player.
 */
@Composable
fun AudioTrackCard(
    track: AudioTrackEntity,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val context = LocalContext.current
    var peaks by remember(track.id, track.filePath) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(track.id, track.filePath) {
        peaks = withContext(Dispatchers.IO) {
            runCatching { WaveformExtractor.extractWaveform(File(track.filePath), WaveformBars) }.getOrNull()
        }
    }
    val fileSize = remember(track.filePath) {
        Formatter.formatShortFileSize(context, File(track.filePath).length())
    }
    val seconds = (track.durationMs / 1000).toInt()
    val length = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.mediumRadius))
            .background(tokens.surfaceVariant.copy(alpha = 0.5f))
            .padding(tokens.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                iconRes = if (playing) WishyIcons.Pause else WishyIcons.Play,
                contentDescription = stringResource(if (playing) R.string.ui_audio_stop_preview else R.string.ui_audio_play_preview),
                selected = playing,
                onClick = onTogglePlay
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = tokens.spaceXs)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.ui_audio_track_info, track.startFrame + 1, length, fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.onSurfaceVariant,
                    maxLines = 1
                )
            }
            ActionIconButton(
                iconRes = WishyIcons.Delete,
                contentDescription = stringResource(R.string.ui_remove_audio),
                onClick = onDelete
            )
        }
        val bars = peaks
        val barColor = if (playing) tokens.primary else tokens.primary.copy(alpha = 0.6f)
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val n = bars?.size ?: WaveformBars
            val slot = this.size.width / n
            val barW = (slot * 0.6f).coerceAtLeast(1f)
            for (i in 0 until n) {
                val v = (bars?.getOrNull(i) ?: 0.05f).coerceIn(0.05f, 1f)
                val h = this.size.height * v
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(i * slot + (slot - barW) / 2f, (this.size.height - h) / 2f),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f)
                )
            }
        }
    }
}

private const val WaveformBars = 60
