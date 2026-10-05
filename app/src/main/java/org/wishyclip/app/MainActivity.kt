package org.wishyclip.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import org.wishyclip.app.ui.design.themes.FlipDarkTokens
import org.wishyclip.app.ui.screens.DesignGalleryScreen
import org.wishyclip.app.ui.screens.EditorScreen
import org.wishyclip.app.ui.screens.ExportScreen
import org.wishyclip.app.ui.screens.HomeScreen
import org.wishyclip.app.ui.screens.NewProjectScreen
import org.wishyclip.app.ui.screens.OnboardingScreen
import org.wishyclip.app.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentTokens by remember { mutableStateOf<WishyTokens>(FlipDarkTokens) }
            var isLeftHanded by remember { mutableStateOf(false) }

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
                WishyNavHost(
                    currentTokens = currentTokens,
                    onSelectTokens = { currentTokens = it },
                    isLeftHanded = isLeftHanded,
                    onToggleLeftHanded = { isLeftHanded = it }
                )
            }
        }
    }
}

@Composable
fun WishyNavHost(
    currentTokens: WishyTokens,
    onSelectTokens: (WishyTokens) -> Unit,
    isLeftHanded: Boolean,
    onToggleLeftHanded: (Boolean) -> Unit
) {
    val nav = rememberNavController()
    val application = LocalContext.current.applicationContext as Application
    NavHost(navController = nav, startDestination = "home") {
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
                onOpenDesignGallery = { nav.navigate("design_gallery") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("design_gallery") {
            DesignGalleryScreen(onBack = { nav.popBackStack() })
        }
        composable("onboarding") {
            OnboardingScreen(onFinish = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } })
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
                onOpenDesignGallery = { nav.navigate("design_gallery") }
            )
        }
    }
}
