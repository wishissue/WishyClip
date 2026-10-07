package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeImporterTest {

    @Test fun parsesHexVariants() {
        assertEquals(Color(0xFFFF6F3C), ThemeImporter.parseColorOrNull("#FF6F3C"))
        assertEquals(Color(0xFFFF8800), ThemeImporter.parseColorOrNull("#f80"))
        assertNull(ThemeImporter.parseColorOrNull("#GG0000"))
        assertNull(ThemeImporter.parseColorOrNull("#12345"))
        assertNull(ThemeImporter.parseColorOrNull(null))
    }

    @Test fun validatesThemeFiles() {
        assertTrue(ThemeImporter.isThemeJson("{\"primary\":\"#112233\"}"))
        assertFalse(ThemeImporter.isThemeJson("{\"foo\":\"bar\"}"))
        assertFalse(ThemeImporter.isThemeJson("hello"))
        assertFalse(ThemeImporter.isThemeJson("{\"primary\":\"nope\"}"))
    }

    @Test fun darkThemeGetsReadableText() {
        val t = ThemeImporter.parseJson("{\"name\":\"Ink\",\"surface\":\"#101014\",\"primary\":\"#FF80AB\"}")
        assertTrue(t.isDark)
        assertEquals("Ink", t.name)
        assertTrue(t.onSurface.luminance() > 0.5f)
        assertTrue(t.onPrimary.luminance() < 0.3f)
    }

    @Test fun lightThemeGetsReadableText() {
        val t = ThemeImporter.parseJson("{\"surface\":\"#FFF4EC\",\"primary\":\"#1B3A8A\"}")
        assertFalse(t.isDark)
        assertEquals(ThemeImporter.DEFAULT_NAME, t.name)
        assertEquals(Color.White, t.onPrimary)
    }

    @Test fun roundTripKeepsEveryColor() {
        val original = ThemeImporter.parseJson("{\"name\":\"A \\\"q\\\"\",\"surface\":\"#101014\",\"primary\":\"#FF80AB\"}")
        val back = ThemeImporter.parseJson(ThemeImporter.toJson(original))
        assertEquals(original.name, back.name)
        assertEquals(original.isDark, back.isDark)
        assertEquals(ThemeImporter.colorsOf(original), ThemeImporter.colorsOf(back))
    }
}
