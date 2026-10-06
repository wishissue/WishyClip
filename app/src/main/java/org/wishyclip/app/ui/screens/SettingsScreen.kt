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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import org.wishyclip.app.ui.components.bouncyClickable
import org.wishyclip.app.ui.design.themes.CloudTokens
import org.wishyclip.app.ui.design.themes.MidnightTokens
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
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
import org.wishyclip.app.ui.design.themes.FlipDarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens

@Composable
fun SettingsScreen(
    currentTokens: WishyTokens,
    onSelectTokens: (WishyTokens) -> Unit,
    isLeftHanded: Boolean,
    onToggleLeftHanded: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onToggleHaptics: (Boolean) -> Unit,
    palmRejection: Boolean,
    onTogglePalmRejection: (Boolean) -> Unit,
    onOpenDesignGallery: () -> Unit,
    onBack: () -> Unit,
    onSelectIconPack: (IconPack) -> Unit = {}
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = tokens.spaceLarge, vertical = tokens.spaceXs),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceSmall)
                ) {
                    listOf(CloudTokens, MidnightTokens, FlipDarkTokens, LightTokens, DarkTokens, AmoledTokens, CandyTokens)
                        .forEach { option ->
                            ThemeChip(
                                name = option.name,
                                dot = option.primary,
                                selected = currentTokens.name == option.name,
                                onClick = { onSelectTokens(option) }
                            )
                        }
                }

                SectionHeader(title = stringResource(R.string.setting_icon_pack))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        WishyIcons.currentPack = IconPack.CUTE
                        onSelectIconPack(IconPack.CUTE)
                    }) {
                        Text("Cute (Color)", color = if (WishyIcons.currentPack == IconPack.CUTE) tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = {
                        WishyIcons.currentPack = IconPack.CLEAN
                        onSelectIconPack(IconPack.CLEAN)
                    }) {
                        Text("Clean (Minimal)", color = if (WishyIcons.currentPack == IconPack.CLEAN) tokens.primary else tokens.onSurface)
                    }
                }

                SectionHeader(title = "Drawing & Touch")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.setting_haptics),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.onSurface
                    )
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = onToggleHaptics
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.setting_palm_rejection),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.onSurface
                    )
                    Switch(
                        checked = palmRejection,
                        onCheckedChange = onTogglePalmRejection
                    )
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
                    Text("Open Design Gallery (Debug)")
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
            hapticsEnabled = true,
            onToggleHaptics = {},
            palmRejection = false,
            onTogglePalmRejection = {},
            onOpenDesignGallery = {},
            onBack = {}
        )
    }
}


/** Big rounded chip showing a theme's accent color and name. */
@Composable
private fun ThemeChip(name: String, dot: Color, selected: Boolean, onClick: () -> Unit) {
    val tokens = WishyTheme.tokens
    Row(
        modifier = Modifier
            .bouncyClickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) tokens.primaryContainer else tokens.surfaceVariant)
            .border(
                width = 2.dp,
                color = if (selected) tokens.primary else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = tokens.spaceMedium, vertical = tokens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.onPrimaryContainer else tokens.onSurface
        )
    }
}
