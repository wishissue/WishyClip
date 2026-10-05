package org.wishyclip.app.canvas

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wishyclip.app.model.Tool

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
}
