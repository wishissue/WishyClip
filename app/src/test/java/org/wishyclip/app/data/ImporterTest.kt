package org.wishyclip.app.data

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImporterTest {

    @Test
    fun testScaleToFit() {
        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.RED)

        val scaled = Importer.scaleToFit(src, 100, 100)
        assertNotNull(scaled)
        assertEquals(100, scaled.width)
        assertEquals(100, scaled.height)
    }
}
