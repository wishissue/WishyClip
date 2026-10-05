package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun TimelineFrameCell(
    frameIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(if (selected) tokens.primaryContainer else tokens.surfaceVariant)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.onSurfaceVariant,
                shape = RoundedCornerShape(tokens.smallRadius)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${frameIndex + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.onPrimaryContainer else tokens.onSurfaceVariant
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
