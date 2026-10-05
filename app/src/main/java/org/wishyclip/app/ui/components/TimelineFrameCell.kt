package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/** One frame in the timeline: a small sheet of "paper" with its number. */
@Composable
fun TimelineFrameCell(
    frameIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 46.dp)
            .clip(shape)
            .background(tokens.paper)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.surfaceVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = "${frameIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) tokens.primary else Color(0xFF6B6B73),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 3.dp)
        )
    }
}

@Preview(name = "TimelineFrameCell Selected")
@Composable
private fun TimelineFrameCellSelectedPreview() {
    WishyTheme(tokens = LightTokens) {
        TimelineFrameCell(frameIndex = 0, selected = true, onClick = {})
    }
}

@Preview(name = "TimelineFrameCell Unselected")
@Composable
private fun TimelineFrameCellUnselectedPreview() {
    WishyTheme(tokens = DarkTokens) {
        TimelineFrameCell(frameIndex = 2, selected = false, onClick = {})
    }
}
