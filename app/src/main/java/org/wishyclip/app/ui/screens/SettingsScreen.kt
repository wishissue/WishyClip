package org.wishyclip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import org.wishyclip.app.R
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.SectionHeader
import org.wishyclip.app.ui.components.ThemeStudioDialog
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.design.IconPack
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.ui.design.themes.ThemeImporter

@Composable
fun SettingsScreen(
    currentTokens: WishyTokens,
    onSelectTokens: (WishyTokens) -> Unit,
    followsSystemTheme: Boolean = false,
    onUseSystemTheme: () -> Unit = {},
    isLeftHanded: Boolean,
    onToggleLeftHanded: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onToggleHaptics: (Boolean) -> Unit,
    palmRejection: Boolean,
    onTogglePalmRejection: (Boolean) -> Unit,
    onOpenDesignGallery: () -> Unit,
    onBack: () -> Unit,
    onSelectIconPack: (IconPack) -> Unit = {},
    customThemes: List<WishyTokens> = emptyList(),
    onSaveCustomTheme: (WishyTokens) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {}
) {
    val tokens = WishyTheme.tokens
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showThemeStudio by remember { mutableStateOf(false) }
    var studioEditsCurrent by remember { mutableStateOf(false) }
    var themeMessage by remember { mutableStateOf<String?>(null) }
    val currentIsCustom = customThemes.any { it.name.equals(currentTokens.name, ignoreCase = true) }

    val themePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readTextLimited(context, uri)
        when {
            text == null -> themeMessage = "Could not read that file (maximum 256 KB)."
            !ThemeImporter.isThemeJson(text) ->
                themeMessage = "That doesn't look like a Wishy Clip theme. It needs a JSON object with colors such as \"primary\" or \"surface\"."
            else -> {
                val imported = ThemeImporter.parseJson(text)
                onSaveCustomTheme(imported)
                themeMessage = "Imported theme \"${imported.name}\""
            }
        }
    }

    WishyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding() // edge-to-edge: keep content clear of the status/gesture bars
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
                    (SettingsStore.BuiltInThemes + customThemes)
                        .forEach { option ->
                            ThemeChip(
                                name = option.name,
                                dot = option.primary,
                                selected = currentTokens.name == option.name,
                                onClick = { onSelectTokens(option) }
                            )
                        }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spaceXs)
                ) {
                    TextButton(onClick = onUseSystemTheme) {
                        Text(
                            text = if (followsSystemTheme) "Following system light/dark" else "Follow system light/dark",
                            color = if (followsSystemTheme) tokens.primary else tokens.onSurface
                        )
                    }
                    TextButton(onClick = {
                        themeMessage = null
                        studioEditsCurrent = false
                        showThemeStudio = true
                    }) { Text(stringResource(R.string.ui_new_theme)) }
                    TextButton(onClick = {
                        themeMessage = null
                        themePicker.launch("*/*")
                    }) { Text(stringResource(R.string.ui_import_theme_json)) }
                    if (currentIsCustom) {
                        TextButton(onClick = {
                            themeMessage = null
                            studioEditsCurrent = true
                            showThemeStudio = true
                        }) { Text("Edit \"${currentTokens.name}\"") }
                        TextButton(onClick = {
                            onDeleteCustomTheme(currentTokens.name)
                            themeMessage = "Deleted \"${currentTokens.name}\""
                        }) { Text(stringResource(R.string.ui_delete), color = tokens.danger) }
                    }
                }
                themeMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.onSurfaceVariant
                    )
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
                        Text(stringResource(R.string.ui_cute_color), color = if (WishyIcons.currentPack == IconPack.CUTE) tokens.primary else tokens.onSurface)
                    }
                    TextButton(onClick = {
                        WishyIcons.currentPack = IconPack.CLEAN
                        onSelectIconPack(IconPack.CLEAN)
                    }) {
                        Text(stringResource(R.string.ui_clean_minimal), color = if (WishyIcons.currentPack == IconPack.CLEAN) tokens.primary else tokens.onSurface)
                    }
                }

                SectionHeader(title = stringResource(R.string.ui_drawing_touch))
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
                Text(
                    text = stringResource(R.string.setting_palm_rejection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.onSurfaceVariant
                )

                SectionHeader(title = stringResource(R.string.ui_layout_accessibility))
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

                if (isDebuggable) {
                    SectionHeader(title = stringResource(R.string.ui_debug_developer_tools))
                    TextButton(
                        onClick = onOpenDesignGallery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ui_open_design_gallery_debug))
                    }
                }

                SectionHeader(title = stringResource(R.string.ui_legal_attributions))
                TextButton(
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.setting_licenses))
                }
            }
        }
    }

    if (showThemeStudio) {
        ThemeStudioDialog(
            initial = if (studioEditsCurrent) currentTokens else currentTokens.copy(name = "My Theme"),
            isEditing = studioEditsCurrent,
            onSave = {
                onSaveCustomTheme(it)
                showThemeStudio = false
                themeMessage = "Saved theme \"${it.name}\""
            },
            onDismiss = { showThemeStudio = false }
        )
    }

    if (showLicensesDialog) {
        WishyDialog(
            title = stringResource(R.string.ui_attributions_open_source_licenses),
            onDismissRequest = { showLicensesDialog = false },
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showLicensesDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSmall)) {
                Text(stringResource(R.string.ui_androidx_jetpack_compose_material_3_ap))
                Text(stringResource(R.string.ui_kotlin_coroutines_room_datastore_apach))
                Text(stringResource(R.string.ui_media3_exoplayer_coil_apache_2_0))
                Text(stringResource(R.string.ui_animated_gif_lzw_encoder_mit_apache_2))
                Text(stringResource(R.string.ui_lz4_lz4_java_apache_2_0))
                Text(stringResource(R.string.ui_inter_typeface_sil_open_font_license_1))
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


/** Reads a small text file picked by the user; null if unreadable or bigger than [maxBytes]. */
private fun readTextLimited(context: Context, uri: Uri, maxBytes: Int = 256 * 1024): String? =
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0
            var tooBig = false
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    tooBig = true
                    break
                }
                out.write(buf, 0, n)
            }
            if (tooBig) null else out.toString(Charsets.UTF_8.name())
        }
    } catch (e: Exception) {
        null
    }
