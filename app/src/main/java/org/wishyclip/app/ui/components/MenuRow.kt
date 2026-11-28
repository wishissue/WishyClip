package org.wishyclip.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme

/**
 * A touch-friendly row for dialog "menus" (export, import, audio): icon, title and an optional
 * one-line description. Replaces the plain text-only buttons those dialogs used.
 */
@Composable
fun MenuRow(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val tokens = WishyTheme.tokens
    val accent = if (tint == Color.Unspecified) tokens.primary else tint
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape12)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMedium)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.onSurface.copy(alpha = alpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
    }
}

private val RoundedCornerShape12 = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
