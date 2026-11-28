package org.wishyclip.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.wishyclip.app.ui.EditorViewModel
import org.wishyclip.app.ui.EditorViewModelFactory
import org.wishyclip.app.ui.HomeViewModel
import org.wishyclip.app.ui.components.BusyOverlay
import org.wishyclip.app.ui.components.LocalHapticsEnabled
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.screens.DesignGalleryScreen
import org.wishyclip.app.ui.screens.EditorScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.ui.components.TimelineMode
import org.wishyclip.app.ui.screens.HomeScreen
import org.wishyclip.app.ui.screens.NewProjectScreen
import org.wishyclip.app.ui.screens.OnboardingScreen
import org.wishyclip.app.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = SettingsStore(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            val savedThemeName by settingsStore.themeName.collectAsState(initial = "")
            val savedIsLeftHanded by settingsStore.isLeftHanded.collectAsState(initial = false)
            val savedTimelineMode by settingsStore.timelineMode.collectAsState(initial = "COMPACT")
            val savedHaptics by settingsStore.hapticsEnabled.collectAsState(initial = true)
            val savedPalmRejection by settingsStore.palmRejection.collectAsState(initial = false)
            val savedMagnifier by settingsStore.magnifier.collectAsState(initial = true)

            val customThemesLoaded by settingsStore.customThemes.collectAsState(initial = null)
            val customThemes = customThemesLoaded ?: emptyList()
            // null while the first value is still loading, so the first-run screen never flashes by.
            val onboardingDone by settingsStore.onboardingDone.collectAsState(initial = null)
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            val systemDark = isSystemInDarkTheme()
            val currentTokens = remember(savedThemeName, customThemes, systemDark) {
                SettingsStore.getThemeTokensByName(savedThemeName, customThemes, systemDark)
            }

            // Keep status/navigation bar icons readable against the active theme.
            DisposableEffect(currentTokens.isDark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val barStyle = if (currentTokens.isDark) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose { }
            }

            CompositionLocalProvider(LocalHapticsEnabled provides savedHaptics) {
            WishyTheme(tokens = currentTokens) {
                val done = onboardingDone
                if (done == null || customThemesLoaded == null) {
                    Box(Modifier.fillMaxSize().background(currentTokens.surface))
                } else {
                    Box(Modifier.fillMaxSize()) {
                        WishyNavHost(
                            currentTokens = currentTokens,
                            onSelectTokens = { tokens ->
                                scope.launch { settingsStore.saveThemeName(tokens.name) }
                            },
                            followsSystemTheme = savedThemeName.isBlank(),
                            onUseSystemTheme = {
                                scope.launch { settingsStore.saveThemeName("") }
                            },
                            isLeftHanded = savedIsLeftHanded,
                            onToggleLeftHanded = { left ->
                                scope.launch { settingsStore.saveIsLeftHanded(left) }
                            },
                            hapticsEnabled = savedHaptics,
                            onToggleHaptics = { haptics ->
                                scope.launch { settingsStore.saveHaptics(haptics) }
                            },
                            palmRejection = savedPalmRejection,
                            onTogglePalmRejection = { palm ->
                                scope.launch { settingsStore.savePalmRejection(palm) }
                            },
                            magnifier = savedMagnifier,
                            onToggleMagnifier = { on -> scope.launch { settingsStore.saveMagnifier(on) } },
                            timelineMode = runCatching { TimelineMode.valueOf(savedTimelineMode) }.getOrDefault(TimelineMode.COMPACT),
                            onSelectTimelineMode = { mode ->
                                scope.launch { settingsStore.saveTimelineMode(mode.name) }
                            },
                            customThemes = customThemes,
                            onSaveCustomTheme = { theme ->
                                scope.launch {
                                    // Saving also selects it, so imports/new themes apply immediately.
                                    val stored = settingsStore.saveCustomTheme(theme)
                                    settingsStore.saveThemeName(stored.name)
                                }
                            },
                            onDeleteCustomTheme = { name ->
                                scope.launch {
                                    settingsStore.deleteCustomTheme(name)
                                    if (savedThemeName.equals(name, ignoreCase = true)) {
                                        settingsStore.saveThemeName("")
                                    }
                                }
                            },
                            showOnboarding = done == false,
                            onOnboardingFinished = { scope.launch { settingsStore.saveOnboardingDone(true) } },
                            showDebugTools = isDebuggable
                        )
                        // One overlay for the whole app: import and export progress is visible on every screen.
                        BusyOverlay(Modifier.fillMaxSize())
                    }
                }
            }
            }
        }
    }
}

@Composable
fun WishyNavHost(
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
    timelineMode: TimelineMode = TimelineMode.COMPACT,
    onSelectTimelineMode: (TimelineMode) -> Unit = {},
    customThemes: List<WishyTokens> = emptyList(),
    onSaveCustomTheme: (WishyTokens) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {},
    showOnboarding: Boolean = false,
    onOnboardingFinished: () -> Unit = {},
    showDebugTools: Boolean = false
) {
    val nav = rememberNavController()
    // Fixed at first composition: changing the start destination later would rebuild the nav graph.
    val startDestination = remember { if (showOnboarding) "onboarding" else "home" }
    val application = LocalContext.current.applicationContext as Application
    NavHost(navController = nav, startDestination = startDestination) {
        composable("home") {
            val homeVm: HomeViewModel = viewModel()
            HomeScreen(
                vm = homeVm,
                onOpenProject = { id -> nav.navigate("editor/$id") },
                onNewProject = { nav.navigate("new_project") },
                onOpenSettings = { nav.navigate("settings") }
            )
            // Update dialogs live on the start screen only, so they never interrupt drawing.
            org.wishyclip.app.update.UpdatePrompt(org.wishyclip.app.update.activityUpdateViewModel())
        }
        composable("new_project") {
            val homeVm: HomeViewModel = viewModel()
            val pickFolder = org.wishyclip.app.ui.components.rememberFolderPicker { homeVm.chooseFolder(it) }
            val rootUri by homeVm.rootUri.collectAsState()
            NewProjectScreen(
                folderCard = {
                    org.wishyclip.app.ui.components.ProjectFolderCard(
                        status = homeVm.folderStatus, rootUri = rootUri, onPick = pickFolder, compact = true
                    )
                },
                onCreateProject = { name, w, h, fps ->
                    homeVm.create(name, w, h, fps) { newId ->
                        nav.navigate("editor/$newId") {
                            popUpTo("home")
                        }
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                currentTokens = currentTokens,
                onSelectTokens = onSelectTokens,
                followsSystemTheme = followsSystemTheme,
                onUseSystemTheme = onUseSystemTheme,
                isLeftHanded = isLeftHanded,
                onToggleLeftHanded = onToggleLeftHanded,
                hapticsEnabled = hapticsEnabled,
                onToggleHaptics = onToggleHaptics,
                palmRejection = palmRejection,
                onTogglePalmRejection = onTogglePalmRejection,
                magnifier = magnifier,
                onToggleMagnifier = onToggleMagnifier,
                timelineMode = timelineMode,
                onSelectTimelineMode = onSelectTimelineMode,
                customThemes = customThemes,
                onSaveCustomTheme = onSaveCustomTheme,
                onDeleteCustomTheme = onDeleteCustomTheme,
                onOpenDesignGallery = { nav.navigate("design_gallery") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("design_gallery") {
            DesignGalleryScreen(onBack = { nav.popBackStack() })
        }
        composable("onboarding") {
            OnboardingScreen(onFinish = {
                onOnboardingFinished()
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            val vm: EditorViewModel = viewModel(factory = EditorViewModelFactory(application, id))
            EditorScreen(
                vm = vm,
                onExit = { nav.popBackStack() },
                isLeftHanded = isLeftHanded,
                palmRejection = palmRejection,
                magnifier = magnifier,
                defaultTimelineMode = timelineMode,
                onOpenDesignGallery = if (showDebugTools) { { nav.navigate("design_gallery") } } else null
            )
        }
    }
}
