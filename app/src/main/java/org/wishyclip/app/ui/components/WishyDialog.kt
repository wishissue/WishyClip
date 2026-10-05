package org.wishyclip.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        title = { Text(text = title) },
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
