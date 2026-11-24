package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

/** Standard Material 3 modal bottom sheet (default container, drag handle and window insets). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            content()
        }
    }
}

@Preview(name = "WishyBottomSheet Light")
@Composable
private fun WishyBottomSheetLightPreview() {
    WishyTheme(tokens = LightTokens) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.ui_bottom_sheet_content))
        }
    }
}

@Preview(name = "WishyBottomSheet Dark")
@Composable
private fun WishyBottomSheetDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.ui_bottom_sheet_dark_content))
        }
    }
}
