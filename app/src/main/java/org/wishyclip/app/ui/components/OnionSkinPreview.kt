package org.wishyclip.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyTheme

/**
 * A tiny live sketch of what the onion skin settings do: a ball moving along an arc, with ghosts of
 * the [before] frames (previous tint) and [after] frames (next tint) at the given [opacity], and the
 * current frame solid. Purely illustrative; it never touches the drawing.
 */
@Composable
fun OnionSkinPreview(
    before: Int,
    after: Int,
    opacity: Float,
    tintBefore: Color,
    tintAfter: Color,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(tokens.smallRadius))
                .background(tokens.paper)
        ) {
            val slots = before + 1 + after
            val pad = 18.dp.toPx()
            val usable = size.width - pad * 2
            val radius = 9.dp.toPx()
            fun pos(i: Int): Offset {
                val t = if (slots <= 1) 0.5f else i / (slots - 1f)
                // gentle arc, like a bouncing ball
                val y = size.height * (0.72f - 0.4f * (1f - (2f * t - 1f) * (2f * t - 1f)))
                return Offset(pad + usable * t, y)
            }
            // farthest ghosts first, so nearer ones sit on top
            for (d in before downTo 1) {
                val fade = opacity * (1f - (d - 1) * 0.12f).coerceAtLeast(0.3f)
                drawCircle(tintBefore.copy(alpha = fade.coerceIn(0f, 1f)), radius, pos(before - d))
            }
            for (d in after downTo 1) {
                val fade = opacity * (1f - (d - 1) * 0.12f).coerceAtLeast(0.3f)
                drawCircle(tintAfter.copy(alpha = fade.coerceIn(0f, 1f)), radius, pos(before + d))
            }
            drawCircle(Color(0xFF222222), radius, pos(before))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Legend(tintBefore, stringResource(R.string.ui_onion_preview_before))
            Legend(Color(0xFF222222), stringResource(R.string.ui_onion_preview_now))
            Legend(tintAfter, stringResource(R.string.ui_onion_preview_after))
        }
        Text(
            text = stringResource(R.string.ui_onion_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.onSurfaceVariant
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = WishyTheme.tokens.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
