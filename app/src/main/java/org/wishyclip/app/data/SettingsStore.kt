package org.wishyclip.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.wishyclip.app.canvas.OnionSkinSettings
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.AmoledTokens
import org.wishyclip.app.ui.design.themes.CandyTokens
import org.wishyclip.app.ui.design.themes.CloudTokens
import org.wishyclip.app.ui.design.themes.CoralDarkTokens
import org.wishyclip.app.ui.design.themes.CoralLightTokens
import org.wishyclip.app.ui.design.themes.MaterialYouTokens
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.FlipDarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import org.wishyclip.app.ui.design.themes.MidnightTokens
import org.wishyclip.app.ui.design.themes.IndigoDarkTokens
import org.wishyclip.app.ui.design.themes.IndigoDuskTokens
import org.wishyclip.app.ui.design.themes.IndigoLightTokens
import org.wishyclip.app.ui.design.themes.IndigoOledTokens
import org.wishyclip.app.ui.design.themes.WishyBlueDarkTokens
import org.wishyclip.app.ui.design.themes.WishyBlueDuskTokens
import org.wishyclip.app.ui.design.themes.WishyBlueLightTokens
import org.wishyclip.app.ui.design.themes.WishyBlueOledTokens
import org.wishyclip.app.ui.design.themes.WishyDarkTokens
import org.wishyclip.app.ui.design.themes.WishyLightTokens
import org.wishyclip.app.ui.design.themes.ThemeImporter
import org.wishyclip.app.brush.MiniJson

// A damaged settings file is replaced by defaults instead of crashing the app at start-up. Projects live
// elsewhere (database + project folder), so losing preferences never loses work.
private val Context.settingsDataStore by preferencesDataStore(
    name = "wishy_settings",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Remembers the last brush settings, theme, and onion skin settings between sessions. */
class SettingsStore(private val context: Context) {
    /** Preferences that never throw while reading: an I/O problem yields defaults. */
    private val prefsFlow: Flow<androidx.datastore.preferences.core.Preferences> =
        context.settingsDataStore.data.catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
    private val keyColor = intPreferencesKey("brush_color")
    private val keySize = floatPreferencesKey("brush_size")
    private val keyOpacity = floatPreferencesKey("brush_opacity")

    private val keyOnionEnabled = booleanPreferencesKey("onion_enabled")
    private val keyOnionBefore = intPreferencesKey("onion_before")
    private val keyOnionAfter = intPreferencesKey("onion_after")
    private val keyOnionOpacity = floatPreferencesKey("onion_opacity")
    private val keyOnionTintBefore = intPreferencesKey("onion_tint_before")
    private val keyOnionTintAfter = intPreferencesKey("onion_tint_after")

    private val keyThemeName = stringPreferencesKey("theme_name")
    private val keyIsLeftHanded = booleanPreferencesKey("is_left_handed")
    private val keyTimelineMode = stringPreferencesKey("timeline_mode")
    private val keyDefaultFps = intPreferencesKey("default_fps")
    private val keyHaptics = booleanPreferencesKey("haptics_enabled")
    private val keyStarterBrushes = androidx.datastore.preferences.core.intPreferencesKey("starter_brush_version")
    private val keyPalmRejection = booleanPreferencesKey("palm_rejection")
    private val keyMagnifier = booleanPreferencesKey("drawing_magnifier")
    private val keyUpdateChecks = booleanPreferencesKey("update_checks")
    private val keyCustomThemes = stringSetPreferencesKey("custom_themes")
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyProjectsRoot = stringPreferencesKey("projects_root_uri")

    val brushColor: Flow<Int> = prefsFlow.map { it[keyColor] ?: DEFAULT_COLOR }
    val brushSize: Flow<Float> = prefsFlow.map { it[keySize] ?: 8f }
    val brushOpacity: Flow<Float> = prefsFlow.map { it[keyOpacity] ?: 1f }

    val themeName: Flow<String> = prefsFlow.map { it[keyThemeName] ?: "" }
    val isLeftHanded: Flow<Boolean> = prefsFlow.map { it[keyIsLeftHanded] ?: false }
    /** Size the timeline opens at: MINIMIZED, COMPACT or EXPANDED (see TimelineMode). */
    val timelineMode: Flow<String> = prefsFlow.map { it[keyTimelineMode] ?: "COMPACT" }
    val defaultFps: Flow<Int> = prefsFlow.map { it[keyDefaultFps] ?: 12 }
    val hapticsEnabled: Flow<Boolean> = prefsFlow.map { it[keyHaptics] ?: true }
    val palmRejection: Flow<Boolean> = prefsFlow.map { it[keyPalmRejection] ?: false }
    /** Look for a newer release on GitHub when the app starts; on by default. */
    val updateChecks: Flow<Boolean> = prefsFlow.map { it[keyUpdateChecks] ?: true }
    /** Magnifier loupe while drawing; on by default. */
    val magnifier: Flow<Boolean> = prefsFlow.map { it[keyMagnifier] ?: true }

    /** The folder (document tree URI) the user chose for their projects, or null when none was chosen. */
    val projectsRootUri: Flow<String?> = prefsFlow.map { it[keyProjectsRoot] }

    suspend fun saveProjectsRootUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(keyProjectsRoot) else it[keyProjectsRoot] = uri
        }
    }

    val onboardingDone: Flow<Boolean> = prefsFlow.map { it[keyOnboardingDone] ?: false }

    /** Themes the user imported or built in the Theme Studio, sorted by name. */
    val customThemes: Flow<List<WishyTokens>> = prefsFlow.map { prefs ->
        (prefs[keyCustomThemes] ?: emptySet())
            .mapNotNull { json -> try { ThemeImporter.parseJson(json) } catch (e: Exception) { null } }
            .sortedBy { it.name.lowercase() }
    }

    val onionSettings: Flow<OnionSkinSettings> = prefsFlow.map {
        OnionSkinSettings(
            enabled = it[keyOnionEnabled] ?: false,
            framesBefore = it[keyOnionBefore] ?: 1,
            framesAfter = it[keyOnionAfter] ?: 0,
            opacity = it[keyOnionOpacity] ?: 0.35f,
            tintBefore = it[keyOnionTintBefore] ?: 0xFFFF4040.toInt(),
            tintAfter = it[keyOnionTintAfter] ?: 0xFF40C040.toInt()
        )
    }

    suspend fun saveBrush(color: Int, size: Float, opacity: Float) {
        context.settingsDataStore.edit {
            it[keyColor] = color
            it[keySize] = size
            it[keyOpacity] = opacity
        }
    }

    suspend fun saveOnionSettings(s: OnionSkinSettings) {
        context.settingsDataStore.edit {
            it[keyOnionEnabled] = s.enabled
            it[keyOnionBefore] = s.framesBefore
            it[keyOnionAfter] = s.framesAfter
            it[keyOnionOpacity] = s.opacity
            it[keyOnionTintBefore] = s.tintBefore
            it[keyOnionTintAfter] = s.tintAfter
        }
    }

    suspend fun saveOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[keyOnboardingDone] = done }
    }

    /**
     * Saves [tokens] as a custom theme (replacing one with the same name) and returns what was
     * stored. A name that collides with a built-in theme gets " (custom)" appended, because the
     * selected theme is remembered by name.
     */
    suspend fun saveCustomTheme(tokens: WishyTokens): WishyTokens {
        var name = tokens.name.trim().ifBlank { ThemeImporter.DEFAULT_NAME }
        if (BuiltInThemes.any { it.name.equals(name, ignoreCase = true) }) {
            name = (name.take(ThemeImporter.MAX_NAME_LENGTH - 9).trim() + " (custom)")
        }
        val stored = tokens.copy(name = name)
        val json = ThemeImporter.toJson(stored)
        context.settingsDataStore.edit { prefs ->
            val kept = (prefs[keyCustomThemes] ?: emptySet())
                .filterNot { (MiniJson.string(it, "name") ?: "").equals(name, ignoreCase = true) }
            prefs[keyCustomThemes] = (kept + json).toSet()
        }
        return stored
    }

    suspend fun deleteCustomTheme(name: String) {
        context.settingsDataStore.edit { prefs ->
            val kept = (prefs[keyCustomThemes] ?: emptySet())
                .filterNot { (MiniJson.string(it, "name") ?: "").equals(name, ignoreCase = true) }
            prefs[keyCustomThemes] = kept.toSet()
        }
    }

    suspend fun saveThemeName(name: String) {
        context.settingsDataStore.edit {
            it[keyThemeName] = name
        }
    }

    suspend fun saveIsLeftHanded(leftHanded: Boolean) {
        context.settingsDataStore.edit {
            it[keyIsLeftHanded] = leftHanded
        }
    }

    suspend fun saveTimelineMode(mode: String) {
        context.settingsDataStore.edit {
            it[keyTimelineMode] = mode
        }
    }

    suspend fun saveDefaultFps(fps: Int) {
        context.settingsDataStore.edit { it[keyDefaultFps] = fps }
    }

    /** Version of the starter brush pack already installed (0 = never). */
    val starterBrushVersion: Flow<Int> = prefsFlow.map { it[keyStarterBrushes] ?: 0 }

    suspend fun saveStarterBrushVersion(version: Int) {
        context.settingsDataStore.edit { it[keyStarterBrushes] = version }
    }

    suspend fun saveHaptics(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyHaptics] = enabled }
    }

    suspend fun saveUpdateChecks(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyUpdateChecks] = enabled }
    }

    suspend fun saveMagnifier(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyMagnifier] = enabled }
    }

    suspend fun savePalmRejection(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyPalmRejection] = enabled }
    }

    companion object {
        const val DEFAULT_COLOR: Int = -16777216 // 0xFF000000 (opaque black) as signed Int

        val BuiltInThemes = listOf(
            IndigoLightTokens,
            IndigoDarkTokens,
            IndigoDuskTokens,
            IndigoOledTokens,
            WishyBlueLightTokens,
            WishyBlueDarkTokens,
            WishyBlueDuskTokens,
            WishyBlueOledTokens,
            WishyLightTokens,
            WishyDarkTokens,
            CoralLightTokens,
            CoralDarkTokens,
            MaterialYouTokens,
            CloudTokens,
            MidnightTokens,
            FlipDarkTokens,
            LightTokens,
            DarkTokens,
            AmoledTokens,
            CandyTokens
        )

        /**
         * Resolves a saved theme name. A blank name means "no choice yet": follow the system
         * light/dark setting ([systemDark]). An unknown name (e.g. a deleted custom theme) falls
         * back the same way.
         */
        fun getThemeTokensByName(
            name: String,
            custom: List<WishyTokens> = emptyList(),
            systemDark: Boolean = false
        ): WishyTokens {
            val builtIn = BuiltInThemes.find { it.name.equals(name, ignoreCase = true) }
            // Material You has no light/dark of its own: it follows the system setting.
            if (builtIn != null && builtIn.dynamic) return builtIn.copy(isDark = systemDark)
            return builtIn
                ?: custom.find { it.name.equals(name, ignoreCase = true) }
                ?: if (systemDark) IndigoDarkTokens else IndigoLightTokens
        }
    }
}
