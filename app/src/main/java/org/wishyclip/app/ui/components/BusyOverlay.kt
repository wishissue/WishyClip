package org.wishyclip.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.wishyclip.app.data.BusyState
import org.wishyclip.app.data.BusyTracker
import org.wishyclip.app.ui.design.WishyTheme
import kotlin.math.roundToInt

/**
 * Full-screen loading overlay for imports and exports. Dims and blocks the editor underneath
 * (so nothing can be drawn mid-import), shows a determinate bar with a percentage when the task
 * reports progress, an indeterminate one when it does not, and a Cancel button when cancellable.
 */
@Composable
fun BusyOverlay(modifier: Modifier = Modifier) {
    val busy by BusyTracker.state.collectAsState()
    val notice by BusyTracker.notice.collectAsState()
    val context = LocalContext.current
    // The export service finishes in the background; tell the user how it ended even without notification permission.
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            BusyTracker.consumeNotice()
        }
    }
    BusyOverlayContent(busy, modifier)
}

@Composable
fun BusyOverlayContent(busy: BusyState?, modifier: Modifier = Modifier) {
    val tokens = WishyTheme.tokens
    AnimatedVisibility(visible = busy != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        // Keep showing the last state while fading out.
        val shown = busy ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                // Swallow every touch so the canvas underneath cannot be drawn on.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(tokens.mediumRadius),
                color = tokens.surface,
                shadowElevation = tokens.elevationMedium,
                modifier = Modifier
                    .padding(tokens.spaceLarge)
                    .widthIn(min = 260.dp, max = 360.dp)
            ) {
                Column(
                    modifier = Modifier.padding(tokens.spaceLarge),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                ) {
                    Text(
                        text = shown.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.onSurface
                    )
                    val p = shown.progress
                    if (p == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = tokens.primary,
                            trackColor = tokens.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                            color = tokens.primary,
                            trackColor = tokens.surfaceVariant
                        )
                    }
                    val line = listOfNotNull(
                        shown.detail.takeIf { it.isNotBlank() },
                        p?.let { "${(it * 100).roundToInt()}%" }
                    ).joinToString("  ·  ")
                    if (line.isNotEmpty()) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.onSurfaceVariant
                        )
                    }
                    shown.onCancel?.let { cancel ->
                        TextButton(onClick = cancel, modifier = Modifier.align(Alignment.End)) {
                            Text("Cancel", color = tokens.danger)
                        }
                    }
                }
            }
        }
    }
}
