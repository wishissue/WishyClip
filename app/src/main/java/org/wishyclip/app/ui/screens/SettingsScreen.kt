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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.SectionHeader
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.design.IconPack
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.AmoledTokens
import org.wishyclip.app.ui.design.themes.CandyTokens
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun SettingsScreen(
    currentTokens: WishyTokens,
    onSelectTokens: (WishyTokens) -> Unit,
    isLeftHanded: Boolean,
    onToggleLeftHanded: (Boolean) -> Unit,
    onOpenDesignGallery: () -> Unit,
    onBack: () -> Unit
) {
    val tokens = WishyTheme.tokens
    val scrollState = rememberScrollState()

    var showLicensesDialog by remember { mutableStateOf(false) }

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
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface
                    )
                }

                SectionHeader(title = stringResource(R.string.setting_theme))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { onSelectTokens(LightTokens) }) {
                        Text("Light", color = if (currentTokens.name == "Light") tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = { onSelectTokens(DarkTokens) }) {
                        Text("Dark", color = if (currentTokens.name == "Dark") tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = { onSelectTokens(AmoledTokens) }) {
                        Text("AMOLED", color = if (currentTokens.name == "AMOLED Black") tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = { onSelectTokens(CandyTokens) }) {
                        Text("Candy", color = if (currentTokens.name == "Candy") tokens.primary else tokens.onSurface)
                    }
                }

                SectionHeader(title = stringResource(R.string.setting_icon_pack))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { WishyIcons.currentPack = IconPack.CUTE }) {
                        Text("Cute (Color)", color = if (WishyIcons.currentPack == IconPack.CUTE) tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = { WishyIcons.currentPack = IconPack.CLEAN }) {
                        Text("Clean (Minimal)", color = if (WishyIcons.currentPack == IconPack.CLEAN) tokens.primary else tokens.onSurface)
                    }
                }

                SectionHeader(title = "Layout & Accessibility")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.setting_left_handed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.onSurface
                    )
                    Switch(
                        checked = isLeftHanded,
                        onCheckedChange = onToggleLeftHanded
                    )
                }

                SectionHeader(title = "Debug & Developer Tools")
                TextButton(
                    onClick = onOpenDesignGallery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎨 Open Design Gallery (Debug)")
                }

                SectionHeader(title = "Legal & Attributions")
                TextButton(
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.setting_licenses))
                }
            }
        }
    }

    if (showLicensesDialog) {
        WishyDialog(
            title = "Attributions & Open Source Licenses",
            onDismissRequest = { showLicensesDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showLicensesDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                Text("• AndroidX, Jetpack Compose, Material 3 - Apache 2.0")
                Text("• Kotlin Coroutines, Room, DataStore - Apache 2.0")
                Text("• Media3 ExoPlayer, Coil - Apache 2.0")
                Text("• Animated GIF LZW Encoder - MIT / Apache 2.0")
            }
        }
    }
}

@Preview(name = "SettingsScreen Light")
@Composable
private fun SettingsScreenLightPreview() {
    WishyTheme(tokens = LightTokens) {
        SettingsScreen(
            currentTokens = LightTokens,
            onSelectTokens = {},
            isLeftHanded = false,
            onToggleLeftHanded = {},
            onOpenDesignGallery = {},
            onBack = {}
        )
    }
}
