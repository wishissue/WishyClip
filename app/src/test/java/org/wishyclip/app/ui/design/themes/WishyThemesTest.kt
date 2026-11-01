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
        assertEquals(WishyLightTokens, SettingsStore.getThemeTokensByName("", systemDark = false))
        assertEquals(WishyDarkTokens, SettingsStore.getThemeTokensByName("", systemDark = true))
    }

    @Test fun unknownNameFallsBackToSystemSetting() {
        assertEquals(WishyDarkTokens, SettingsStore.getThemeTokensByName("Deleted theme", systemDark = true))
    }

    @Test fun savedBuiltInChoiceIsKeptRegardlessOfSystem() {
        assertEquals(CloudTokens, SettingsStore.getThemeTokensByName("Cloud", systemDark = true))
        assertEquals(WishyLightTokens, SettingsStore.getThemeTokensByName("wishy", systemDark = true))
    }

    @Test fun darkFlagsMatchPalettes() {
        assertFalse(WishyLightTokens.isDark)
        assertTrue(WishyDarkTokens.isDark)
    }

    @Test fun themeNamesAreUnique() {
        val names = SettingsStore.BuiltInThemes.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun textAndPrimaryMeetWcagAa() {
        for (t in listOf(WishyLightTokens, WishyDarkTokens)) {
            assertTrue("${t.name} body", contrast(t.onSurface, t.surface) >= 4.5f)
            assertTrue("${t.name} body on panel", contrast(t.onSurface, t.toolRail) >= 4.5f)
            assertTrue("${t.name} secondary", contrast(t.onSurfaceVariant, t.toolRail) >= 4.5f)
            assertTrue("${t.name} on-primary", contrast(t.onPrimary, t.primary) >= 4.5f)
            assertTrue("${t.name} primary on panel", contrast(t.primary, t.toolRail) >= 3f)
            assertTrue("${t.name} on-primaryContainer", contrast(t.onPrimaryContainer, t.primaryContainer) >= 4.5f)
        }
    }
}
