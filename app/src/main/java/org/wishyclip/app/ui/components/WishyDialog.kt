package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/**
 * Material 3 [AlertDialog]: title, scrollable body, a text-button dismiss action and a
 * text-button confirm action. Pass `dismissText = null` for a single-button dialog.
 */
@Composable
fun WishyDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String = stringResource(R.string.action_ok),
    onConfirm: () -> Unit,
    dismissText: String? = stringResource(R.string.action_cancel),
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Colors the confirm button as dangerous (delete, discard, overwrite). */
    destructive: Boolean = false,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(title) },
        text = {
            // Scrolls when the content is taller than the dialog (small phones, landscape, long lists).
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = WishyTheme.tokens.danger)
                else ButtonDefaults.textButtonColors()
            ) { Text(confirmText) }
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            { TextButton(onClick = onDismiss) { Text(dismissText) } }
        } else null
    )
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
        Text(stringResource(R.string.ui_recover_work_body, projectName))
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
