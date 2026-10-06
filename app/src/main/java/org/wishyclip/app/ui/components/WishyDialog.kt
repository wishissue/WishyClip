package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(WishyTheme.tokens.largeRadius),
        containerColor = WishyTheme.tokens.surface,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            if (dismissText != null && onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissText)
                }
            }
        }
    )
}

@Composable
fun ShortcutDialog(
    onDismissRequest: () -> Unit
) {
    WishyDialog(
        title = "Keyboard Shortcuts",
        onDismissRequest = onDismissRequest,
        confirmText = "Close",
        onConfirm = onDismissRequest,
        dismissText = null,
        onDismiss = null
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
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
        title = "Unsaved Changes Found",
        onDismissRequest = onDiscard,
        confirmText = "Recover Project",
        onConfirm = onRecover,
        dismissText = "Discard",
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
            title = "Delete Frame?",
            onDismissRequest = {},
            confirmText = "Delete",
            onConfirm = {},
            dismissText = "Cancel",
            onDismiss = {}
        ) {
            Text("Frame 2 will be deleted.")
        }
    }
}

@Preview(name = "WishyDialog Dark")
@Composable
private fun WishyDialogDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        WishyDialog(
            title = "Project Settings",
            onDismissRequest = {},
            confirmText = "Save",
            onConfirm = {}
        ) {
            Text("Project settings content")
        }
    }
}
