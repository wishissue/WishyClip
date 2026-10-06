package org.wishyclip.app.ui

import androidx.compose.runtime.Composable
import org.wishyclip.app.ui.components.ColorPickerSheet

@Composable
fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ColorPickerSheet(
        initialColor = initial,
        onPick = onPick,
        onDismiss = onDismiss
    )
}
