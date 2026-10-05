package org.wishyclip.app.canvas

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wishyclip.app.model.Tool
import org.wishyclip.app.model.displayName

@RunWith(RobolectricTestRunner::class)
class ExtraToolsTest {

    @Test
    fun testExtraBrushPaints() {
        val airbrush = BrushPaints.create(Tool.AIRBRUSH, Color.RED, size = 10f, opacity = 0.5f)
        assertNotNull(airbrush)
        assertEquals(Color.RED, airbrush.color)

        val calligraphy = BrushPaints.create(Tool.CALLIGRAPHY, Color.BLUE, size = 12f, opacity = 1.0f)
        assertNotNull(calligraphy)

        val highlighter = BrushPaints.create(Tool.HIGHLIGHTER, Color.YELLOW, size = 20f, opacity = 0.4f)
        assertNotNull(highlighter)
    }

    @Test
    fun testNewBrushesBuildPaints() {
        for (brush in org.wishyclip.app.model.BRUSH_TOOLS) {
            val paint = BrushPaints.create(brush, Color.BLACK, size = 10f, opacity = 1f)
            assertNotNull(brush.name, paint)
            assert(paint.strokeWidth >= 1f) { "${brush.name} has no stroke width" }
        }
        // Pixel pen is the only brush with hard, aliased edges.
        assertEquals(false, BrushPaints.create(Tool.PIXEL, Color.BLACK, 8f, 1f).isAntiAlias)
        assertEquals(true, BrushPaints.create(Tool.INK, Color.BLACK, 8f, 1f).isAntiAlias)
    }

    @Test
    fun testBrushMenuContainsElevenBrushes() {
        assertEquals(11, org.wishyclip.app.model.BRUSH_TOOLS.size)
        assertEquals("Pixel Pen", Tool.PIXEL.displayName)
    }
}
