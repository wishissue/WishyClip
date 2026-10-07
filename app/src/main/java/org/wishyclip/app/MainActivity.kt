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
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
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
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.CloudTokens
import org.wishyclip.app.ui.screens.DesignGalleryScreen
import org.wishyclip.app.ui.screens.EditorScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.wishyclip.app.data.SettingsStore
import org.wishyclip.app.ui.design.IconPack
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.screens.ExportScreen
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
            val savedThemeName by settingsStore.themeName.collectAsState(initial = "Cloud")
            val savedIsLeftHanded by settingsStore.isLeftHanded.collectAsState(initial = false)
            val savedIconPack by settingsStore.iconPack.collectAsState(initial = IconPack.CUTE.name)
            val savedHaptics by settingsStore.hapticsEnabled.collectAsState(initial = true)
            val savedPalmRejection by settingsStore.palmRejection.collectAsState(initial = false)

            val customThemesLoaded by settingsStore.customThemes.collectAsState(initial = null)
            val customThemes = customThemesLoaded ?: emptyList()
            // null while the first value is still loading, so the first-run screen never flashes by.
            val onboardingDone by settingsStore.onboardingDone.collectAsState(initial = null)
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            val currentTokens = remember(savedThemeName, customThemes) {
                SettingsStore.getThemeTokensByName(savedThemeName, customThemes)
            }

            LaunchedEffect(savedIconPack) {
                WishyIcons.currentPack = try {
                    IconPack.valueOf(savedIconPack)
                } catch (_: Exception) {
                    IconPack.CUTE
                }
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

            WishyTheme(tokens = currentTokens) {
                val done = onboardingDone
                if (done == null || customThemesLoaded == null) {
                    Box(Modifier.fillMaxSize().background(currentTokens.surface))
                } else {
                    WishyNavHost(
                        currentTokens = currentTokens,
                        onSelectTokens = { tokens ->
                            scope.launch { settingsStore.saveThemeName(tokens.name) }
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
                        onSelectIconPack = { pack ->
                            scope.launch { settingsStore.saveIconPack(pack.name) }
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
                                    settingsStore.saveThemeName(CloudTokens.name)
                                }
                            }
                        },
                        showOnboarding = done == false,
                        onOnboardingFinished = { scope.launch { settingsStore.saveOnboardingDone(true) } },
                        showDebugTools = isDebuggable
                    )
                }
            }
        }
    }
}

@Composable
fun WishyNavHost(
    currentTokens: WishyTokens,
    onSelectTokens: (WishyTokens) -> Unit,
    isLeftHanded: Boolean,
    onToggleLeftHanded: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onToggleHaptics: (Boolean) -> Unit,
    palmRejection: Boolean,
    onTogglePalmRejection: (Boolean) -> Unit,
    onSelectIconPack: (IconPack) -> Unit = {},
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
        }
        composable("new_project") {
            val homeVm: HomeViewModel = viewModel()
            NewProjectScreen(
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
                isLeftHanded = isLeftHanded,
                onToggleLeftHanded = onToggleLeftHanded,
                hapticsEnabled = hapticsEnabled,
                onToggleHaptics = onToggleHaptics,
                palmRejection = palmRejection,
                onTogglePalmRejection = onTogglePalmRejection,
                onSelectIconPack = onSelectIconPack,
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
            route = "export/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("projectId") ?: 0L
            ExportScreen(
                projectId = id,
                projectName = "Project $id",
                onBack = { nav.popBackStack() }
            )
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
                onOpenDesignGallery = if (showDebugTools) { { nav.navigate("design_gallery") } } else null
            )
        }
    }
}
