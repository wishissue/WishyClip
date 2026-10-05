package org.wishyclip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ColorSwatch
import org.wishyclip.app.ui.components.ProjectCard
import org.wishyclip.app.ui.components.SectionHeader
import org.wishyclip.app.ui.components.TimelineFrameCell
import org.wishyclip.app.ui.components.ToolButton
import org.wishyclip.app.ui.components.WishySlider
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.AmoledTokens
import org.wishyclip.app.ui.design.themes.CandyTokens
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun DesignGalleryScreen(onBack: () -> Unit) {
    var activeTokens by remember { mutableStateOf<WishyTokens>(LightTokens) }
    val scrollState = rememberScrollState()

    WishyTheme(tokens = activeTokens) {
        val tokens = WishyTheme.tokens
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(tokens.spaceLarge)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceLarge)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ActionIconButton(
                        iconRes = WishyIcons.Back,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Text(
                        text = "Design Gallery",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.onSurface
                    )
                }

                SectionHeader(title = "Theme Switcher (${activeTokens.name})")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { activeTokens = LightTokens }) { Text("Light") }
                    TextButton(onClick = { activeTokens = DarkTokens }) { Text("Dark") }
                    TextButton(onClick = { activeTokens = AmoledTokens }) { Text("AMOLED") }
                    TextButton(onClick = { activeTokens = CandyTokens }) { Text("Candy") }
                }

                SectionHeader(title = "Tool Buttons")
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    ToolButton(iconRes = WishyIcons.Pen, description = "Pen", selected = true, onClick = {})
                    ToolButton(iconRes = WishyIcons.Pencil, description = "Pencil", selected = false, onClick = {})
                    ToolButton(iconRes = WishyIcons.Eraser, description = "Eraser", selected = false, onClick = {})
                    ToolButton(iconRes = WishyIcons.Fill, description = "Fill", selected = false, onClick = {})
                }

                SectionHeader(title = "Action Icon Buttons")
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    ActionIconButton(iconRes = WishyIcons.Undo, contentDescription = "Undo", onClick = {})
                    ActionIconButton(iconRes = WishyIcons.Redo, contentDescription = "Redo", onClick = {})
                    ActionIconButton(iconRes = WishyIcons.Play, contentDescription = "Play", onClick = {})
                    ActionIconButton(iconRes = WishyIcons.Onion, contentDescription = "Onion", selected = true, onClick = {})
                }

                SectionHeader(title = "WishySlider")
                var sliderVal by remember { mutableStateOf(32f) }
                WishySlider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    valueRange = 1f..100f,
                    label = "Brush Size",
                    unit = "px"
                )

                SectionHeader(title = "Color Swatches")
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    ColorSwatch(color = Color.Red, selected = true, onClick = {})
                    ColorSwatch(color = Color.Green, selected = false, onClick = {})
                    ColorSwatch(color = Color.Blue, selected = false, onClick = {})
                }

                SectionHeader(title = "Timeline Frame Cell")
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    TimelineFrameCell(frameIndex = 0, selected = true, onClick = {})
                    TimelineFrameCell(frameIndex = 1, selected = false, onClick = {})
                    TimelineFrameCell(frameIndex = 2, selected = false, onClick = {})
                }

                SectionHeader(title = "Project Card")
                ProjectCard(
                    title = "Sample Animation Project",
                    info = "24 frames • 12 FPS",
                    thumbnailFile = null,
                    onClick = {},
                    onLongClick = {}
                )
            }
        }
    }
}

@Preview(name = "Design Gallery Preview")
@Composable
private fun DesignGalleryPreview() {
    DesignGalleryScreen(onBack = {})
}
