package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.wishyclip.app.brush.MiniJson
import org.wishyclip.app.ui.design.WishyTokens
import java.io.File
import java.util.Locale

/**
 * Reads and writes theme packs: a flat JSON object such as
 *
 *     { "name": "Sunset", "isDark": false, "primary": "#FF6F3C", "surface": "#FFF4EC" }
 *
 * Every key is optional. Colors may be `#RRGGBB`, `#AARRGGBB` or `#RGB`. Anything that is left out
 * is derived from the colors that were given (so text on a new primary color stays readable)
 * or falls back to the built-in Light / Dark theme.
 */
object ThemeImporter {

    const val DEFAULT_NAME = "Custom Theme"
    const val MAX_NAME_LENGTH = 32

    /** Every color key a theme file may contain, in the order they are written out. */
    val COLOR_KEYS = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "canvasBackdrop", "toolRail", "timeline", "accent", "danger", "paper",
        "onionPreviousTint", "onionNextTint"
    )

    /** Parses "#RRGGBB", "#AARRGGBB" or "#RGB" (the '#' is optional). Null when it is not a color. */
    fun parseColorOrNull(hex: String?): Color? {
        if (hex == null) return null
        var clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (clean.length == 3) clean = clean.map { "$it$it" }.joinToString("")
        if (clean.length != 6 && clean.length != 8) return null
        if (clean.any { Character.digit(it, 16) < 0 }) return null
        val value = clean.toLongOrNull(16) ?: return null
        return if (clean.length == 6) Color((0xFF000000L or value).toInt()) else Color(value.toInt())
    }

    fun parseColorHex(hex: String, defaultColor: Color): Color = parseColorOrNull(hex) ?: defaultColor

    /** True if [text] looks like a theme file: a JSON object with at least one valid color. */
    fun isThemeJson(text: String): Boolean {
        val t = text.trim().removePrefix("\uFEFF").trim()
        if (!t.startsWith("{") || !t.endsWith("}")) return false
        return COLOR_KEYS.any { parseColorOrNull(MiniJson.string(t, it)) != null }
    }

    /** Black-ish or white, whichever is easier to read on [background]. */
    fun contrastOn(background: Color): Color =
        if (background.luminance() > 0.179f) Color(0xFF1C1B1F) else Color.White

    /**
     * Builds tokens from a theme JSON string. Missing colors are derived (see class docs);
     * [base] only supplies spacing/shape values and fallbacks, and defaults to Light or Dark
     * depending on the theme's own `isDark` flag / surface brightness.
     */
    fun parseJson(jsonString: String, base: WishyTokens? = null): WishyTokens {
        val text = jsonString.trim().removePrefix("\uFEFF")
        fun given(key: String): Color? = parseColorOrNull(MiniJson.string(text, key))

        val surfaceGiven = given("surface")
        val dark = MiniJson.bool(text, "isDark")
            ?: surfaceGiven?.let { it.luminance() < 0.5f }
            ?: base?.isDark
            ?: false
        val b = base ?: if (dark) DarkTokens else LightTokens

        val surface = surfaceGiven ?: b.surface
        val onSurface = given("onSurface") ?: if (surfaceGiven != null) contrastOn(surface) else b.onSurface

        val primaryGiven = given("primary")
        val primary = primaryGiven ?: b.primary
        val primaryContainer = given("primaryContainer")
            ?: if (primaryGiven != null) lerp(surface, primary, if (dark) 0.40f else 0.22f) else b.primaryContainer
        val onPrimary = given("onPrimary") ?: contrastOn(primary)
        val onPrimaryContainer = given("onPrimaryContainer") ?: contrastOn(primaryContainer)

        val secondaryGiven = given("secondary")
        val secondary = secondaryGiven ?: b.secondary
        val secondaryContainer = given("secondaryContainer")
            ?: if (secondaryGiven != null) lerp(surface, secondary, if (dark) 0.40f else 0.22f) else b.secondaryContainer
        val onSecondary = given("onSecondary") ?: contrastOn(secondary)
        val onSecondaryContainer = given("onSecondaryContainer") ?: contrastOn(secondaryContainer)

        val surfaceVariant = given("surfaceVariant")
            ?: if (surfaceGiven != null) lerp(surface, onSurface, 0.10f) else b.surfaceVariant
        val onSurfaceVariant = given("onSurfaceVariant") ?: lerp(onSurface, surface, 0.28f)

        val canvasBackdrop = given("canvasBackdrop")
            ?: if (surfaceGiven != null) lerp(surface, Color.Black, if (dark) 0.35f else 0.08f) else b.canvasBackdrop
        val chrome = if (surfaceGiven != null) lerp(surface, onSurface, 0.05f) else null
        val toolRail = given("toolRail") ?: chrome ?: b.toolRail
        val timeline = given("timeline") ?: chrome ?: b.timeline

        val name = (MiniJson.string(text, "name") ?: "")
            .filter { it >= ' ' }
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifBlank { DEFAULT_NAME }

        return b.copy(
            name = name,
            isDark = dark,
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            canvasBackdrop = canvasBackdrop,
            toolRail = toolRail,
            timeline = timeline,
            accent = given("accent") ?: b.accent,
            danger = given("danger") ?: b.danger,
            paper = given("paper") ?: b.paper,
            onionPreviousTint = given("onionPreviousTint") ?: b.onionPreviousTint,
            onionNextTint = given("onionNextTint") ?: b.onionNextTint,
            glassBorder = if (surfaceGiven != null) onSurface.copy(alpha = 0.12f) else b.glassBorder
        )
    }

    /** All color values of [t] keyed like the JSON file. */
    fun colorsOf(t: WishyTokens): List<Pair<String, Color>> = listOf(
        "primary" to t.primary, "onPrimary" to t.onPrimary,
        "primaryContainer" to t.primaryContainer, "onPrimaryContainer" to t.onPrimaryContainer,
        "secondary" to t.secondary, "onSecondary" to t.onSecondary,
        "secondaryContainer" to t.secondaryContainer, "onSecondaryContainer" to t.onSecondaryContainer,
        "surface" to t.surface, "onSurface" to t.onSurface,
        "surfaceVariant" to t.surfaceVariant, "onSurfaceVariant" to t.onSurfaceVariant,
        "canvasBackdrop" to t.canvasBackdrop, "toolRail" to t.toolRail, "timeline" to t.timeline,
        "accent" to t.accent, "danger" to t.danger, "paper" to t.paper,
        "onionPreviousTint" to t.onionPreviousTint, "onionNextTint" to t.onionNextTint
    )

    fun toHex(c: Color): String = "#" + String.format(Locale.US, "%08X", c.toArgb())

    /** Serialises [t] so that `parseJson(toJson(t))` gives the same colors back. */
    fun toJson(t: WishyTokens): String {
        val sb = StringBuilder("{\n")
        sb.append("  \"name\": ").append(MiniJson.quote(t.name)).append(",\n")
        sb.append("  \"isDark\": ").append(t.isDark)
        for ((key, color) in colorsOf(t)) {
            sb.append(",\n  \"").append(key).append("\": \"").append(toHex(color)).append('"')
        }
        sb.append("\n}\n")
        return sb.toString()
    }

    fun loadFromFile(file: File, base: WishyTokens? = null): WishyTokens? {
        return try {
            if (!file.exists() || file.length() > 256 * 1024) return null
            val text = file.readText()
            if (isThemeJson(text)) parseJson(text, base) else null
        } catch (e: Exception) {
            null
        }
    }
}
