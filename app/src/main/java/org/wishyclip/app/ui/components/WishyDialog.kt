package org.wishyclip.app.ui.components

import org.wishyclip.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun WishyDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = WishyTheme.tokens

    // This dialog is an in-tree overlay, not a window: without this, Back left the whole editor.
    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismissRequest() })
            },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = modifier
                .padding(tokens.spaceLarge)
                .widthIn(max = 380.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(tokens.largeRadius)
        ) {
            Column(
                modifier = Modifier
                    .padding(tokens.spaceLarge)
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceMedium)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.onSurface
                )

                // Scrolls when the content is taller than the dialog, so the buttons below never get
                // pushed off-screen (small phones, landscape, long font lists...).
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    content()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissText != null && onDismiss != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .clickable(role = Role.Button) { onDismiss() }
                                .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceSmall)
                        ) {
                            Text(
                                text = dismissText,
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(tokens.smallRadius))
                            .background(tokens.primary)
                            .clickable(role = Role.Button) { onConfirm() }
                            .padding(horizontal = tokens.spaceLarge, vertical = tokens.spaceSmall)
                    ) {
                        Text(
                            text = confirmText,
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutDialog(
    onDismissRequest: () -> Unit
) {
    WishyDialog(
        title = stringResource(R.string.ui_keyboard_shortcuts),
        onDismissRequest = onDismissRequest,
        confirmText = stringResource(R.string.ui_close),
        onConfirm = onDismissRequest,
        dismissText = null,
        onDismiss = null
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val shortcuts = listOf(
                "B" to "Brush tool",
                "E" to "Eraser tool",
                "F" to "Fill tool",
                "L" to "Lasso tool",
                "T" to "Text tool",
                "R" to "Ruler toggle",
                "M" to "Mirror symmetry toggle",
                "O" to "Onion skin toggle",
                "Space" to "Play / Pause",
                "Left / Right" to "Previous / Next frame",
                "Ctrl + Z" to "Undo",
                "Ctrl + Shift + Z" to "Redo"
            )
            shortcuts.forEach { (key, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ProjectRecoveryDialog(
    projectName: String,
    onRecover: () -> Unit,
    onDiscard: () -> Unit
) {
    WishyDialog(
        title = stringResource(R.string.ui_unsaved_changes_found),
        onDismissRequest = onDiscard,
        confirmText = stringResource(R.string.ui_recover_project),
        onConfirm = onRecover,
        dismissText = stringResource(R.string.ui_discard),
        onDismiss = onDiscard
    ) {
        Text("Unsaved artwork changes were found for \"$projectName\". Would you like to recover your work?")
    }
}

@Preview(name = "WishyDialog Light")
@Composable
private fun WishyDialogLightPreview() {
    WishyTheme(tokens = LightTokens) {
        WishyDialog(
            title = stringResource(R.string.ui_delete_frame_2),
            onDismissRequest = {},
            confirmText = stringResource(R.string.ui_delete),
            onConfirm = {},
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = {}
        ) {
            Text(stringResource(R.string.ui_frame_2_will_be_deleted))
        }
    }
}

@Preview(name = "WishyDialog Dark")
@Composable
private fun WishyDialogDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        WishyDialog(
            title = stringResource(R.string.ui_project_settings),
            onDismissRequest = {},
            confirmText = stringResource(R.string.ui_save),
            onConfirm = {}
        ) {
            Text(stringResource(R.string.ui_project_settings_content))
        }
    }
}
