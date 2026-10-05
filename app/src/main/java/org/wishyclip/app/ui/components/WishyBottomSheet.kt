package org.wishyclip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = WishyTheme.tokens
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = tokens.surface,
        contentColor = tokens.onSurface,
        shape = RoundedCornerShape(topStart = tokens.largeRadius, topEnd = tokens.largeRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spaceMedium)
        ) {
            content()
        }
    }
}

@Preview(name = "WishyBottomSheet Light")
@Composable
private fun WishyBottomSheetLightPreview() {
    WishyTheme(tokens = LightTokens) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightTokens.surface)
                .padding(16.dp)
        ) {
            Column {
                Text("Bottom Sheet Content")
            }
        }
    }
}

@Preview(name = "WishyBottomSheet Dark")
@Composable
private fun WishyBottomSheetDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkTokens.surface)
                .padding(16.dp)
        ) {
            Column {
                Text("Bottom Sheet Dark Content")
            }
        }
    }
}
