package org.wishyclip.app.export

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ExporterTest {

    @Test
    fun testAnimatedGifEncoder() {
        val bmp1 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bmp1.eraseColor(Color.RED)
        val bmp2 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bmp2.eraseColor(Color.BLUE)

        val baos = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        val started = encoder.start(baos)
        assertTrue(started)

        encoder.setDelay(100)
        encoder.addFrame(bmp1)
        encoder.addFrame(bmp2)
        val finished = encoder.finish()
        assertTrue(finished)

        val bytes = baos.toByteArray()
        assertTrue(bytes.isNotEmpty())
        // Verify GIF magic header "GIF89a"
        assertEquals('G'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
    }
}
