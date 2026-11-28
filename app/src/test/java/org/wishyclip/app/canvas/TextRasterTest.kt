package org.wishyclip.app.canvas

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextRasterTest {

    @Test fun blankAndNormalTextProduceSmallNonEmptyBitmaps() {
        val blank = TextRaster.render("", Color.BLACK, 40f, 1f)
        val hello = TextRaster.render("Hello\nWorld", Color.BLACK, 40f, 1f)
        assertTrue(blank.width >= 1 && blank.height >= 1)
        assertTrue(hello.width > blank.width)
        assertTrue(hello.height > 40)
    }

    @Test fun hugeTextIsBoundedInMemory() {
        val text = "W".repeat(50_000) + "\n" + "line\n".repeat(5_000)
        val bmp = TextRaster.render(text, Color.BLACK, 1500f, 1f)
        assertTrue("${bmp.width}x${bmp.height}", bmp.width <= 2048 && bmp.height <= 2048)
        assertTrue(bmp.byteCount <= 2048 * 2048 * 4)
    }

    @Test fun sizeAndOpacityAreClamped() {
        val tiny = TextRaster.render("x", Color.RED, -5f, 7f)
        assertTrue(tiny.width >= 1)
        assertEquals(2000, TextRaster.MAX_CHARS)
    }
}
