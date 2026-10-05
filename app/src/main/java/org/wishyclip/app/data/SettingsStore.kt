package org.wishyclip.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.wishyclip.app.canvas.OnionSkinSettings

private val Context.settingsDataStore by preferencesDataStore(name = "wishy_settings")

/** Remembers the last brush settings and onion skin settings between sessions. */
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

    val brushColor: Flow<Int> = context.settingsDataStore.data.map { it[keyColor] ?: DEFAULT_COLOR }
    val brushSize: Flow<Float> = context.settingsDataStore.data.map { it[keySize] ?: 8f }
    val brushOpacity: Flow<Float> = context.settingsDataStore.data.map { it[keyOpacity] ?: 1f }

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

    companion object {
        const val DEFAULT_COLOR: Int = -16777216 // 0xFF000000 (opaque black) as signed Int
    }
}
