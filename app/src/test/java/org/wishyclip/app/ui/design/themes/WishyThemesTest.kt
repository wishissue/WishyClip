package org.wishyclip.app.ui.design.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wishyclip.app.data.SettingsStore

class WishyThemesTest {

    private fun contrast(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance())
        val l2 = minOf(a.luminance(), b.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    @Test fun blankNameFollowsSystemSetting() {
        assertEquals(IndigoLightTokens, SettingsStore.getThemeTokensByName("", systemDark = false))
        assertEquals(IndigoDarkTokens, SettingsStore.getThemeTokensByName("", systemDark = true))
    }

    @Test fun unknownNameFallsBackToSystemSetting() {
        assertEquals(IndigoDarkTokens, SettingsStore.getThemeTokensByName("Deleted theme", systemDark = true))
    }

    @Test fun savedBuiltInChoiceIsKeptRegardlessOfSystem() {
        assertEquals(CloudTokens, SettingsStore.getThemeTokensByName("Cloud", systemDark = true))
        assertEquals(WishyLightTokens, SettingsStore.getThemeTokensByName("wishy", systemDark = true))
    }

    @Test fun darkFlagsMatchPalettes() {
        assertFalse(WishyLightTokens.isDark)
        assertTrue(WishyDarkTokens.isDark)
        assertFalse(IndigoLightTokens.isDark)
        assertTrue(listOf(IndigoDarkTokens, IndigoDuskTokens, IndigoOledTokens).all { it.isDark })
    }

    @Test fun themeNamesAreUnique() {
        val names = SettingsStore.BuiltInThemes.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun textAndPrimaryMeetWcagAa() {
        for (t in listOf(WishyLightTokens, WishyDarkTokens, IndigoLightTokens, IndigoDarkTokens, IndigoDuskTokens, IndigoOledTokens)) {
            assertTrue("${t.name} body", contrast(t.onSurface, t.surface) >= 4.5f)
            assertTrue("${t.name} body on panel", contrast(t.onSurface, t.toolRail) >= 4.5f)
            assertTrue("${t.name} secondary", contrast(t.onSurfaceVariant, t.toolRail) >= 4.5f)
            assertTrue("${t.name} on-primary", contrast(t.onPrimary, t.primary) >= 4.5f)
            assertTrue("${t.name} primary on panel", contrast(t.primary, t.toolRail) >= 3f)
            assertTrue("${t.name} on-primaryContainer", contrast(t.onPrimaryContainer, t.primaryContainer) >= 4.5f)
        }
    }

    @Test fun paperAlwaysContrastsWithBackdrop() {
        for (t in listOf(IndigoLightTokens, IndigoDarkTokens, IndigoDuskTokens, IndigoOledTokens)) {
            assertTrue("${t.name} paper vs stage", contrast(t.paper, t.canvasBackdrop) >= 1.1f)
        }
    }
}
