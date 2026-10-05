package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FillToolTest {

    @Test
    fun testFillBlankBitmap() {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)

        val tool = ScanlineFillTool()
        val changed = tool.fill(bmp, 25, 25, Color.RED, tolerance = 10)

        assertTrue(changed)
        assertEquals(Color.RED, bmp.getPixel(0, 0))
        assertEquals(Color.RED, bmp.getPixel(25, 25))
        assertEquals(Color.RED, bmp.getPixel(49, 49))
    }

    @Test
    fun testFillSameColorReturnsFalse() {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)

        val tool = ScanlineFillTool()
        val changed = tool.fill(bmp, 5, 5, Color.BLUE, tolerance = 10)

        assertFalse(changed)
    }

    @Test
    fun testFillWithinBoundary() {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)

        // Draw a black boundary box from (10,10) to (40,40)
        for (i in 10..40) {
            bmp.setPixel(i, 10, Color.BLACK)
            bmp.setPixel(i, 40, Color.BLACK)
            bmp.setPixel(10, i, Color.BLACK)
            bmp.setPixel(40, i, Color.BLACK)
        }

        val tool = ScanlineFillTool()
        val changed = tool.fill(bmp, 20, 20, Color.GREEN, tolerance = 5)

        assertTrue(changed)
        // Inside box should be GREEN
        assertEquals(Color.GREEN, bmp.getPixel(20, 20))
        // Outside box should still be WHITE
        assertEquals(Color.WHITE, bmp.getPixel(5, 5))
        // Boundary should still be BLACK
        assertEquals(Color.BLACK, bmp.getPixel(10, 10))
    }
}
