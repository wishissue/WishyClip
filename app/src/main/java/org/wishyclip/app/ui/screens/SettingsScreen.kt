package org.wishyclip.app.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.ui.components.ActionIconButton
import org.wishyclip.app.ui.components.ThemeStudioDialog
import org.wishyclip.app.ui.components.TimelineMode
import org.wishyclip.app.ui.components.WishyDialog
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyMotion
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.rememberReducedMotion
import org.wishyclip.app.ui.design.themes.ThemeImporter

/**
 * Settings, organised as four categories instead of one long list. Only settings that change real
 * behaviour are here (each one is read by the editor or the theme system); there are no placeholders.
 *
 * Phone: the selected category is an expanded card (icon, name, blurb, live preview, controls) and the
 * others collapse to compact rows, so switching never means going back. Wide screens (>= 720dp): a
 * compact category list on the left and the selected category on the right.
 */
private enum class SettingsCategory(val title: String, val blurb: String, val icon: Int) {
    APPEARANCE("Appearance", "Theme and colours of the whole app", WishyIcons.Palette),
    WORKSPACE("Workspace", "Where the tools sit and how much room the timeline takes", WishyIcons.Dashboard),
    INPUT("Pen and touch", "Stylus, palm rejection and haptics", WishyIcons.Stylus),
    PROJECTS("Project folder", "Where your projects are saved, outside the app", WishyIcons.Dashboard),
    ABOUT("About", "Licenses and credits", WishyIcons.Info)
}

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
    magnifier: Boolean = true,
    onToggleMagnifier: (Boolean) -> Unit = {},
    onOpenDesignGallery: () -> Unit,
    onBack: () -> Unit,
    timelineMode: TimelineMode = TimelineMode.COMPACT,
    onSelectTimelineMode: (TimelineMode) -> Unit = {},
    customThemes: List<WishyTokens> = emptyList(),
    onSaveCustomTheme: (WishyTokens) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val reduced = rememberReducedMotion()

    // Survives rotation, so the user keeps their place.
    var selected by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }
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

    // The controls of each category. Everything it shows reacts at once: the theme applies to this very
    // screen, the workspace sketch follows the switches, the pad shows the live pointer.
    val categoryContent: @Composable (SettingsCategory) -> Unit = { category ->
        when (category) {
            SettingsCategory.APPEARANCE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceSketch(
                    tokens = currentTokens,
                    timelineMode = timelineMode,
                    leftHanded = isLeftHanded,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (SettingsStore.BuiltInThemes + customThemes).forEach { option ->
                        ThemeSwatch(
                            option = option,
                            selected = currentTokens.name == option.name,
                            onClick = { onSelectTokens(option) }
                        )
                    }
                }
                SettingSwitchRow(
                    icon = WishyIcons.DarkMode,
                    title = stringResource(R.string.setting_follow_system),
                    subtitle = stringResource(R.string.setting_follow_system_sub),
                    checked = followsSystemTheme,
                    // Turning it off keeps the theme that is showing now as the explicit choice.
                    onCheckedChange = { on -> if (on) onUseSystemTheme() else onSelectTokens(currentTokens) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        }) { Text(stringResource(R.string.setting_edit_theme, currentTokens.name)) }
                        TextButton(onClick = {
                            onDeleteCustomTheme(currentTokens.name)
                            themeMessage = "Deleted \"${currentTokens.name}\""
                        }) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
                themeMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SettingsCategory.WORKSPACE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceSketch(
                    tokens = currentTokens,
                    timelineMode = timelineMode,
                    leftHanded = isLeftHanded,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Timeline size when a project opens",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val modes = TimelineMode.values()
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == timelineMode,
                            onClick = { onSelectTimelineMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                        ) {
                            Text(
                                when (mode) {
                                    TimelineMode.MINIMIZED -> "Minimal"
                                    TimelineMode.COMPACT -> "Compact"
                                    TimelineMode.EXPANDED -> "Expanded"
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    "You can change it any time with the handle on top of the timeline. " +
                        "In landscape the compact timeline is a single slim row.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingSwitchRow(
                    icon = WishyIcons.LeftHand,
                    title = stringResource(R.string.setting_left_handed),
                    subtitle = stringResource(R.string.setting_left_handed_sub),
                    checked = isLeftHanded,
                    onCheckedChange = onToggleLeftHanded
                )
                val toolStore = remember { SettingsStore(context.applicationContext) }
                val toolScope = androidx.compose.runtime.rememberCoroutineScope()
                val pinned by toolStore.pinnedTools.collectAsState(initial = SettingsStore.DEFAULT_PINNED_TOOLS)
                val autoMin by toolStore.toolbarAutoMinimize.collectAsState(initial = true)
                Text(
                    text = stringResource(R.string.ui_toolbar_tools),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                org.wishyclip.app.ui.components.PinnedToolsEditor(
                    pinned = pinned,
                    onPinnedChange = { toolScope.launch { toolStore.savePinnedTools(it) } },
                    autoMinimize = autoMin,
                    onAutoMinimizeChange = { toolScope.launch { toolStore.saveToolbarAutoMinimize(it) } }
                )
            }

            SettingsCategory.INPUT -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InputPad()
                SettingSwitchRow(
                    icon = WishyIcons.Stylus,
                    title = stringResource(R.string.setting_palm_rejection),
                    subtitle = stringResource(R.string.setting_palm_rejection_hint),
                    checked = palmRejection,
                    onCheckedChange = onTogglePalmRejection
                )
                SettingSwitchRow(
                    icon = WishyIcons.Eyedropper,
                    title = stringResource(R.string.setting_magnifier),
                    subtitle = stringResource(R.string.setting_magnifier_sub),
                    checked = magnifier,
                    onCheckedChange = onToggleMagnifier
                )
                SettingSwitchRow(
                    icon = WishyIcons.Haptics,
                    title = stringResource(R.string.setting_haptics),
                    subtitle = stringResource(R.string.setting_haptics_sub),
                    checked = hapticsEnabled,
                    onCheckedChange = onToggleHaptics
                )
            }

            SettingsCategory.PROJECTS -> {
                val homeVm: org.wishyclip.app.ui.HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val rootUri by homeVm.rootUri.collectAsState()
                val pick = org.wishyclip.app.ui.components.rememberFolderPicker { homeVm.chooseFolder(it) }
                org.wishyclip.app.ui.components.ProjectFolderCard(
                    status = homeVm.folderStatus, rootUri = rootUri, onPick = pick
                )
            }
            SettingsCategory.ABOUT -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val updateVm = org.wishyclip.app.update.activityUpdateViewModel()
                if (updateVm.supported) {
                    SettingSwitchRow(
                        icon = WishyIcons.Import,
                        title = stringResource(R.string.setting_update_checks),
                        subtitle = stringResource(R.string.setting_update_checks_sub),
                        checked = updateVm.autoCheck,
                        onCheckedChange = updateVm::updateAutoCheck
                    )
                }
                org.wishyclip.app.update.UpdateStatusRow(updateVm)
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Text(
                    text = stringResource(R.string.about_design_credit_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                Text(
                    text = stringResource(R.string.about_design_credit_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/wishissue") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.about_open_wishissue), modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { showLicensesDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setting_licenses), modifier = Modifier.fillMaxWidth())
                }
                if (isDebuggable) {
                    TextButton(onClick = onOpenDesignGallery, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ui_open_design_gallery_debug), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    WishyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                val wide = maxWidth >= 720.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconButton(
                            iconRes = WishyIcons.Back,
                            contentDescription = stringResource(R.string.action_close),
                            onClick = onBack
                        )
                        Text(
                            text = stringResource(R.string.title_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.width(240.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SettingsCategory.values().forEach { category ->
                                    CompactCategoryRow(category, category == selected, reduced) { selected = category }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 24.dp)
                            ) {
                                ExpandedCategoryCard(category = selected, reduced = reduced) { categoryContent(selected) }
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        // Keep the opened card in view when the user switches category.
                        LaunchedEffect(selected) {
                            listState.animateScrollToItem(selected.ordinal)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            itemsIndexed(SettingsCategory.values().toList(), key = { _, c -> c.name }) { _, category ->
                                if (category == selected) {
                                    ExpandedCategoryCard(category = category, reduced = reduced) { categoryContent(category) }
                                } else {
                                    CompactCategoryRow(category, false, reduced) { selected = category }
                                }
                            }
                        }
                    }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ui_androidx_jetpack_compose_material_3_ap))
                Text(stringResource(R.string.ui_kotlin_coroutines_room_datastore_apach))
                Text(stringResource(R.string.ui_media3_exoplayer_coil_apache_2_0))
                Text(stringResource(R.string.ui_animated_gif_lzw_encoder_mit_apache_2))
                Text(stringResource(R.string.ui_lz4_lz4_java_apache_2_0))
                Text(stringResource(R.string.about_jakarta_license))
                Text(stringResource(R.string.about_bagel_license))
                Text(stringResource(R.string.ui_inter_typeface_sil_open_font_license_1))
                Text(stringResource(R.string.about_design_credit_body))
                Text(stringResource(R.string.about_icons_license))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category navigation
// ---------------------------------------------------------------------------------------------

/** A collapsed category: one 56dp row. Tapping it opens (expands) the category. */
@Composable
private fun CompactCategoryRow(category: SettingsCategory, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        animationSpec = WishyMotion.effects(reduced), label = "categoryRow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(if (selected) WishyIcons.selected(category.icon) else category.icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
        )
        Text(
            category.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            painterResource(WishyIcons.ChevronRight),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = scheme.onSurfaceVariant
        )
    }
}

/** The opened category: a tonal header (icon, name, blurb) over its live preview and controls. */
@Composable
private fun ExpandedCategoryCard(category: SettingsCategory, reduced: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember(category) { mutableStateOf(false) }
    LaunchedEffect(category) { visible = true }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(WishyMotion.spatial(reduced)),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(WishyIcons.selected(category.icon)),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = scheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(category.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(category.blurb, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(WishyMotion.effects(reduced)) + expandVertically(WishyMotion.spatial(reduced)),
                exit = fadeOut(WishyMotion.effects(reduced)) + shrinkVertically(WishyMotion.spatial(reduced))
            ) {
                Column { content() }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Controls and live previews
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingSwitchRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = scheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null) // the whole row toggles, so TalkBack announces one switch
    }
}

/** One theme: its surface, primary and accent colours side by side, plus the name. */
@Composable
private fun ThemeSwatch(option: WishyTokens, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val chip = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(chip)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = chip
                )
        ) {
            Box(Modifier.weight(1.2f).fillMaxHeight().background(option.surface))
            Box(Modifier.weight(1f).fillMaxHeight().background(option.primary))
            Box(
                Modifier.weight(0.8f).fillMaxHeight()
                    .background(if (option.tertiary == Color.Unspecified) option.accent else option.tertiary)
            )
        }
        Text(
            option.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * A small sketch of the editor in portrait, drawn from the real theme tokens: stage, paper, tool rail
 * (on the side chosen by "left-handed") and the timeline at the chosen size. It updates the instant a
 * theme or a workspace setting changes. Proportions are schematic, not pixel-accurate.
 */
@Composable
private fun WorkspaceSketch(
    tokens: WishyTokens,
    timelineMode: TimelineMode,
    leftHanded: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    val timelineFraction = when (timelineMode) {
        TimelineMode.MINIMIZED -> 0.08f
        TimelineMode.COMPACT -> 0.15f
        TimelineMode.EXPANDED -> 0.30f
    }
    val dim = tokens.onSurfaceVariant.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .size(width = 120.dp, height = 210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.canvasBackdrop)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(2) { Box(Modifier.size(6.dp).clip(CircleShape).background(dim)) }
                Spacer(Modifier.weight(1f))
                repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(dim)) }
            }
            // canvas stage with paper and rail
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.paper)
                )
                Column(
                    modifier = Modifier
                        .align(if (leftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.toolRail)
                        .padding(vertical = 4.dp, horizontal = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (i == 0) tokens.primary else dim)
                        )
                    }
                }
            }
            // timeline dock at the chosen size
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(timelineFraction)
                    .animateContentSize(WishyMotion.spatial(reduced))
                    .background(tokens.timeline)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 16.dp, height = 2.dp).clip(CircleShape).background(dim)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tokens.primary))
                    if (timelineMode != TimelineMode.MINIMIZED) {
                        repeat(4) { i ->
                            Box(
                                Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (i == 1) tokens.primary else dim.copy(alpha = 0.4f))
                            )
                        }
                    } else {
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(dim.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

private class PadSegment(val from: Offset, val to: Offset, val width: Float)

/**
 * A scratch pad that shows what the device actually reports: pointer type (finger / stylus / eraser)
 * and pressure, with the stroke width following the pressure. It is a test surface only: it saves
 * nothing and does not emulate palm rejection.
 */
@Composable
private fun InputPad() {
    val scheme = MaterialTheme.colorScheme
    val idle = "Draw here to test your pen or finger"
    val segments = remember { mutableStateListOf<PadSegment>() }
    var report by remember { mutableStateOf(idle) }
    val ink = scheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WishyTheme.tokens.paper)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (!change.pressed) continue
                                val pressure = change.pressure.coerceIn(0f, 1f)
                                val width = 2.dp.toPx() + 8.dp.toPx() * pressure
                                segments.add(PadSegment(change.previousPosition, change.position, width))
                                if (segments.size > 700) repeat(100) { segments.removeAt(0) }
                                val kind = when (change.type) {
                                    PointerType.Stylus -> "Stylus"
                                    PointerType.Eraser -> "Stylus eraser"
                                    PointerType.Mouse -> "Mouse"
                                    else -> "Finger"
                                }
                                report = "$kind · pressure ${"%.2f".format(pressure)}"
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                for (s in segments) {
                    drawLine(color = ink, start = s.from, end = s.to, strokeWidth = s.width, cap = StrokeCap.Round)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                report,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                segments.clear()
                report = idle
            }) { Text(stringResource(R.string.ui_clear)) }
        }
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
