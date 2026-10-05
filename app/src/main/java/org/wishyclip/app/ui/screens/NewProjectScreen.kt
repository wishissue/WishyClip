package org.wishyclip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wishyclip.app.R
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.SectionHeader
import org.wishyclip.app.ui.components.WishySlider
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

enum class AspectPreset(val label: String, val width: Int, val height: Int) {
    SQUARE("Square 1:1 (1080x1080)", 1080, 1080),
    LANDSCAPE("Landscape 16:9 (1920x1080)", 1920, 1080),
    PORTRAIT("Portrait 9:16 (1080x1920)", 1080, 1920),
    CUSTOM("Custom", 1080, 1080)
}

@Composable
fun NewProjectScreen(
    onCreateProject: (name: String, width: Int, height: Int, fps: Int) -> Unit,
    onBack: () -> Unit
) {
    val tokens = WishyTheme.tokens
    var name by remember { mutableStateOf("New Project") }
    var preset by remember { mutableStateOf(AspectPreset.LANDSCAPE) }
    var widthText by remember { mutableStateOf("1920") }
    var heightText by remember { mutableStateOf("1080") }
    var fps by remember { mutableIntStateOf(12) }

    val scrollState = rememberScrollState()

    val widthInt = widthText.toIntOrNull() ?: 1080
    val heightInt = heightText.toIntOrNull() ?: 1080
    val estBytes = widthInt.toLong() * heightInt.toLong() * 4L * 5L
    val estMb = estBytes / (1024 * 1024)

    WishyTheme {
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
                        contentDescription = stringResource(R.string.action_cancel),
                        onClick = onBack
                    )
                    Text(
                        text = stringResource(R.string.title_new_project),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                SectionHeader(title = "Preset Canvas Size")
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                    for (p in AspectPreset.values()) {
                        TextButton(
                            onClick = {
                                preset = p
                                if (p != AspectPreset.CUSTOM) {
                                    widthText = p.width.toString()
                                    heightText = p.height.toString()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = p.label,
                                color = if (preset == p) tokens.primary else tokens.onSurface
                            )
                        }
                    }
                }

                if (preset == AspectPreset.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMedium)
                    ) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = { Text("Width (px)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text("Height (px)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SectionHeader(title = "Frame Rate")
                WishySlider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.roundToInt() },
                    valueRange = 1f..60f,
                    label = "Target FPS",
                    unit = " fps"
                )

                Text(
                    text = "Estimated Cache Memory: ~$estMb MB" + if (estMb > 120) " ⚠️ High resolution requires 3GB+ RAM" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (estMb > 120) tokens.danger else tokens.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateProject(name.trim(), widthInt, heightInt, fps)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceMedium)
                ) {
                    Text(text = stringResource(R.string.action_create))
                }
            }
        }
    }
}

@Preview(name = "NewProjectScreen Light")
@Composable
private fun NewProjectScreenLightPreview() {
    WishyTheme(tokens = LightTokens) {
        NewProjectScreen(onCreateProject = { _, _, _, _ -> }, onBack = {})
    }
}

@Preview(name = "NewProjectScreen Dark")
@Composable
private fun NewProjectScreenDarkPreview() {
    WishyTheme(tokens = DarkTokens) {
        NewProjectScreen(onCreateProject = { _, _, _, _ -> }, onBack = {})
    }
}
