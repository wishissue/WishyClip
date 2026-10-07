package org.wishyclip.app.ui.screens

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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.wishyclip.app.R
import org.wishyclip.app.export.ExportFormat
import org.wishyclip.app.export.ExportService
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.SectionHeader
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun ExportScreen(
    projectId: Long,
    projectName: String,
    onBack: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.MP4) }
    val scrollState = rememberScrollState()

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
                        contentDescription = stringResource(R.string.action_close),
                        onClick = onBack
                    )
                    Text(
                        text = stringResource(R.string.title_export),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface
                    )
                }

                Text(
                    text = "Project: $projectName",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.onSurfaceVariant
                )

                SectionHeader(title = "Select Format")
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                    val formats = listOf(
                        ExportFormat.MP4 to "MP4 Video (H.264, no audio yet)",
                        ExportFormat.GIF to "Animated GIF",
                        ExportFormat.PNG_SEQUENCE to "PNG Sequence (ZIP)",
                        ExportFormat.PNG_CURRENT_FRAME to "Current Frame PNG"
                    )
                    for ((format, label) in formats) {
                        OutlinedCard(
                            onClick = { selectedFormat = format },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(tokens.spaceMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFormat == format,
                                    onClick = { selectedFormat = format }
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = tokens.onSurface,
                                    modifier = Modifier.padding(start = tokens.spaceSmall)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        ExportService.start(context, projectId, selectedFormat)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceMedium)
                ) {
                    Text(text = "Export to Device")
                }
            }
        }
    }
}

@Preview(name = "ExportScreen Preview")
@Composable
private fun ExportScreenPreview() {
    WishyTheme(tokens = LightTokens) {
        ExportScreen(projectId = 1L, projectName = "My Animation", onBack = {})
    }
}
