package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import org.wishyclip.app.ui.design.WishyTokens
import java.io.File

object ThemeImporter {

    fun parseColorHex(hex: String, defaultColor: Color): Color {
        return try {
            val clean = hex.removePrefix("#").trim()
            val parsed = clean.toLong(16)
            if (clean.length == 6) {
                Color((0xFF000000 or parsed).toInt())
            } else if (clean.length == 8) {
                Color(parsed.toInt())
            } else {
                defaultColor
            }
        } catch (e: Exception) {
            defaultColor
        }
    }

    /**
     * Imports a WishyTokens theme from a JSON string.
     */
    fun parseJson(jsonString: String, base: WishyTokens = LightTokens): WishyTokens {
        val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
        val name = nameMatch?.groupValues?.get(1) ?: "Custom Theme"

        fun getColor(key: String, defaultColor: Color): Color {
            val match = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
            val hex = match?.groupValues?.get(1) ?: return defaultColor
            return parseColorHex(hex, defaultColor)
        }

        return base.copy(
            name = name,
            primary = getColor("primary", base.primary),
            primaryContainer = getColor("primaryContainer", base.primaryContainer),
            secondary = getColor("secondary", base.secondary),
            secondaryContainer = getColor("secondaryContainer", base.secondaryContainer),
            surface = getColor("surface", base.surface),
            surfaceVariant = getColor("surfaceVariant", base.surfaceVariant),
            canvasBackdrop = getColor("canvasBackdrop", base.canvasBackdrop),
            toolRail = getColor("toolRail", base.toolRail),
            timeline = getColor("timeline", base.timeline),
            accent = getColor("accent", base.accent),
            danger = getColor("danger", base.danger)
        )
    }

    fun loadFromFile(file: File, base: WishyTokens = LightTokens): WishyTokens? {
        return try {
            if (!file.exists()) null
            else parseJson(file.readText(), base)
        } catch (e: Exception) {
            null
        }
    }
}
