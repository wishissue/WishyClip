package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyTheme

/**
 * Always-visible "the microphone is on" indicator with the elapsed time and a Stop button. The audio dialog
 * can be closed while recording, so the state must be visible from anywhere in the editor.
 */
@Composable
fun RecordingBadge(startedAtElapsedMs: Long, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = WishyTheme.tokens
    var seconds by remember(startedAtElapsedMs) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtElapsedMs) {
        while (true) {
            seconds = ((android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs) / 1000L).coerceAtLeast(0L)
            delay(500)
        }
    }
    val time = "%d:%02d".format(seconds / 60, seconds % 60)
    val description = stringResource(R.string.ui_recording_badge, time)
    val stopLabel = stringResource(R.string.ui_stop_recording)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFB3261E))
            .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
        Text(
            text = stringResource(R.string.ui_recording_time, time),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClickLabel = stopLabel, onClick = onStop)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stopLabel, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
