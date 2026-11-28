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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme

/** A tool that can be pinned to the minimized tool rail. [id] is what gets stored in settings. */
enum class PinnableTool(val id: String, val label: String, @DrawableRes val icon: Int) {
    BRUSH("brush", "Brush", WishyIcons.Pen),
    ERASER("eraser", "Eraser", WishyIcons.Eraser),
    LASSO("lasso", "Lasso", WishyIcons.Lasso),
    FILL("fill", "Fill", WishyIcons.Fill),
    SHAPES("shapes", "Shapes", WishyIcons.Shapes),
    TEXT("text", "Text", WishyIcons.Text),
    EYEDROPPER("eyedropper", "Eyedropper", WishyIcons.Eyedropper),
    MIRROR("mirror", "Mirror", WishyIcons.Mirror),
    RULER("ruler", "Ruler", WishyIcons.Ruler)
}

/**
 * Lets the user choose which tools stay visible when the tool rail minimizes, and whether it
 * minimizes at all. Used by the Settings screen and by the editor's menu.
 */
@Composable
fun PinnedToolsEditor(
    pinned: Set<String>,
    onPinnedChange: (Set<String>) -> Unit,
    autoMinimize: Boolean,
    onAutoMinimizeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToggleRow(
            icon = WishyIcons.Tune,
            title = stringResource(R.string.ui_toolbar_auto_minimize),
            subtitle = stringResource(R.string.ui_toolbar_auto_minimize_sub),
            checked = autoMinimize,
            onCheckedChange = onAutoMinimizeChange
        )
        Text(
            text = stringResource(R.string.ui_toolbar_tools_hint),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = tokens.spaceSmall, vertical = tokens.spaceSmall)
        )
        for (tool in PinnableTool.values()) {
            ToggleRow(
                icon = tool.icon,
                title = tool.label,
                subtitle = null,
                checked = tool.id in pinned,
                onCheckedChange = { on -> onPinnedChange(if (on) pinned + tool.id else pinned - tool.id) }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = WishyTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = tokens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMedium)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tokens.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
