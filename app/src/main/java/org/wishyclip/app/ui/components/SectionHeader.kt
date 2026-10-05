package org.wishyclip.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val tokens = WishyTheme.tokens
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = tokens.onSurface,
        modifier = modifier.padding(vertical = tokens.spaceSmall)
    )
}

@Preview(name = "SectionHeader Light")
@Composable
private fun SectionHeaderLightPreview() {
    WishyTheme(tokens = LightTokens) {
        SectionHeader(title = "Project Settings")
    }
}

@Preview(name = "SectionHeader Dark")
@Composable
private fun SectionHeaderDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        SectionHeader(title = "Layers")
    }
}
