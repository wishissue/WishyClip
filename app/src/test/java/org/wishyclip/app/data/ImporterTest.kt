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

    @Test
    fun testSampleSizeShrinksHugePhotos() {
        // 4000x3000 photo into a 1280x720 canvas: halve while both sides stay >= the target.
        assertEquals(2, Importer.sampleSizeFor(4000, 3000, 1280, 720))
        assertEquals(1, Importer.sampleSizeFor(800, 600, 1280, 720))
    }
}
