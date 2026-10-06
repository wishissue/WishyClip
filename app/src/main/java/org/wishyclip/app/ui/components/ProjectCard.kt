package org.wishyclip.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import java.io.File

/** Project tile: the first frame on "paper" with the name and size underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    title: String,
    info: String,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(tokens.elevationSmall, RoundedCornerShape(tokens.largeRadius))
            .clip(RoundedCornerShape(tokens.largeRadius))
            .background(tokens.toolRail)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(tokens.paper),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailFile != null && thumbnailFile.exists()) {
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Icon(
                    painter = painterResource(WishyIcons.Pencil),
                    contentDescription = title,
                    tint = Color(0xFFC9C9D0),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = tokens.spaceMedium + 2.dp, vertical = tokens.spaceMedium)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "ProjectCard Light")
@Composable
private fun ProjectCardLightPreview() {
    WishyTheme(tokens = LightTokens) {
        ProjectCard(
            title = "My Animation",
            info = "12 frames • 24 FPS",
            thumbnailFile = null,
            onClick = {},
            onLongClick = {}
        )
    }
}

@Preview(name = "ProjectCard Dark")
@Composable
private fun ProjectCardDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        ProjectCard(
            title = "Walk Cycle Test",
            info = "24 frames • 12 FPS",
            thumbnailFile = null,
            onClick = {},
            onLongClick = {}
        )
    }
}
