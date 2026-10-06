package org.wishyclip.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.wishyclip.app.canvas.OnionSkinSettings
import org.wishyclip.app.ui.design.IconPack
import org.wishyclip.app.ui.design.WishyTokens
import org.wishyclip.app.ui.design.themes.AmoledTokens
import org.wishyclip.app.ui.design.themes.CandyTokens
import org.wishyclip.app.ui.design.themes.CloudTokens
import org.wishyclip.app.ui.design.themes.DarkTokens
import org.wishyclip.app.ui.design.themes.FlipDarkTokens
import org.wishyclip.app.ui.design.themes.LightTokens
import org.wishyclip.app.ui.design.themes.MidnightTokens

private val Context.settingsDataStore by preferencesDataStore(name = "wishy_settings")

/** Remembers the last brush settings, theme, and onion skin settings between sessions. */
class SettingsStore(private val context: Context) {
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
    private val keyIconPack = stringPreferencesKey("icon_pack")
    private val keyDefaultFps = intPreferencesKey("default_fps")
    private val keyHaptics = booleanPreferencesKey("haptics_enabled")
    private val keyPalmRejection = booleanPreferencesKey("palm_rejection")

    val brushColor: Flow<Int> = context.settingsDataStore.data.map { it[keyColor] ?: DEFAULT_COLOR }
    val brushSize: Flow<Float> = context.settingsDataStore.data.map { it[keySize] ?: 8f }
    val brushOpacity: Flow<Float> = context.settingsDataStore.data.map { it[keyOpacity] ?: 1f }

    val themeName: Flow<String> = context.settingsDataStore.data.map { it[keyThemeName] ?: "Cloud" }
    val isLeftHanded: Flow<Boolean> = context.settingsDataStore.data.map { it[keyIsLeftHanded] ?: false }
    val iconPack: Flow<String> = context.settingsDataStore.data.map { it[keyIconPack] ?: IconPack.CUTE.name }
    val defaultFps: Flow<Int> = context.settingsDataStore.data.map { it[keyDefaultFps] ?: 12 }
    val hapticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[keyHaptics] ?: true }
    val palmRejection: Flow<Boolean> = context.settingsDataStore.data.map { it[keyPalmRejection] ?: false }

    val onionSettings: Flow<OnionSkinSettings> = context.settingsDataStore.data.map {
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

    suspend fun saveIconPack(pack: String) {
        context.settingsDataStore.edit {
            it[keyIconPack] = pack
        }
    }

    suspend fun saveDefaultFps(fps: Int) {
        context.settingsDataStore.edit { it[keyDefaultFps] = fps }
    }

    suspend fun saveHaptics(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyHaptics] = enabled }
    }

    suspend fun savePalmRejection(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyPalmRejection] = enabled }
    }

    companion object {
        const val DEFAULT_COLOR: Int = -16777216 // 0xFF000000 (opaque black) as signed Int

        val BuiltInThemes = listOf(
            CloudTokens,
            MidnightTokens,
            FlipDarkTokens,
            LightTokens,
            DarkTokens,
            AmoledTokens,
            CandyTokens
        )

        fun getThemeTokensByName(name: String): WishyTokens {
            return BuiltInThemes.find { it.name.equals(name, ignoreCase = true) } ?: CloudTokens
        }
    }
}
