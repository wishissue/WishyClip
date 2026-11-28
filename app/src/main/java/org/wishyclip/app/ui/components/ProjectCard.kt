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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import org.wishyclip.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import java.io.File

/** Whether a project's folder copy is up to date. */
enum class ProjectBadge { NONE, SAVED, PENDING, PROBLEM }

/** Project tile: a Material 3 filled card with the first frame on "paper", the name and the size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    title: String,
    info: String,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: ProjectBadge = ProjectBadge.NONE
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val openLabel = stringResource(R.string.ui_open_project, title)
    val optionsLabel = stringResource(R.string.ui_project_options)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = openLabel,
                onLongClickLabel = optionsLabel,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(WishyTheme.tokens.paper),
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
                    contentDescription = null, // decorative: the card's title text already names it
                    tint = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp)
                )
            }
            if (badge != ProjectBadge.NONE) {
                val (text, container, content) = when (badge) {
                    ProjectBadge.SAVED -> Triple(stringResource(R.string.ui_badge_saved), scheme.primaryContainer, scheme.onPrimaryContainer)
                    ProjectBadge.PENDING -> Triple(stringResource(R.string.ui_badge_pending), scheme.surfaceVariant, scheme.onSurfaceVariant)
                    else -> Triple(stringResource(R.string.ui_badge_problem), scheme.errorContainer, scheme.onErrorContainer)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(container)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
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
