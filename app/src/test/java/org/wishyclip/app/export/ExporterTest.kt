package org.wishyclip.app.export

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
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

    @Test
    fun testExportFormats() {
        assertEquals(4, ExportFormat.entries.size)
        assertEquals(ExportFormat.MP4, ExportFormat.valueOf("MP4"))
        assertEquals(ExportFormat.GIF, ExportFormat.valueOf("GIF"))
        assertEquals(ExportFormat.PNG_SEQUENCE, ExportFormat.valueOf("PNG_SEQUENCE"))
        assertEquals(ExportFormat.PNG_CURRENT_FRAME, ExportFormat.valueOf("PNG_CURRENT_FRAME"))
    }

    @Test
    fun gifFramesDecodeBackToTheOriginalPixels() {
        // Regression: the LZW stream used wrong initial codes / never widened code size, so every GIF was corrupt.
        val bmp = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        for (y in 0 until 90) for (x in 0 until 120) bmp.setPixel(x, y, Color.rgb(x * 2 % 200, y * 2 % 200, (x + y) % 40))
        val baos = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.start(baos)
        encoder.setDelay(40)
        encoder.addFrame(bmp)
        encoder.addFrame(bmp)
        encoder.finish()

        val bytes = baos.toByteArray()

        val imageIOClass = Class.forName("javax.imageio.ImageIO")
        val imageReaderClass = Class.forName("javax.imageio.ImageReader")
        val bufferedImageClass = Class.forName("java.awt.image.BufferedImage")

        val getImageReadersByFormatName = imageIOClass.getMethod("getImageReadersByFormatName", String::class.java)
        val createImageInputStream = imageIOClass.getMethod("createImageInputStream", Any::class.java)

        val readers = (getImageReadersByFormatName.invoke(null, "gif") as Iterator<*>)
        val reader = readers.next()
        val stream = createImageInputStream.invoke(null, ByteArrayInputStream(bytes))

        val setInputMethod = imageReaderClass.getMethod("setInput", Any::class.java)
        setInputMethod.invoke(reader, stream)

        val getNumImagesMethod = imageReaderClass.getMethod("getNumImages", Boolean::class.javaPrimitiveType)
        val numImages = getNumImagesMethod.invoke(reader, true) as Int
        assertEquals(2, numImages)

        val readMethod = imageReaderClass.getMethod("read", Int::class.javaPrimitiveType)
        val decoded = readMethod.invoke(reader, 1) // BufferedImage
        val getWidth = bufferedImageClass.getMethod("getWidth")
        val getHeight = bufferedImageClass.getMethod("getHeight")
        val getRGB = bufferedImageClass.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

        assertEquals(120, getWidth.invoke(decoded) as Int)
        assertEquals(90, getHeight.invoke(decoded) as Int)

        val c = bmp.getPixel(60, 45)
        val d = getRGB.invoke(decoded, 60, 45) as Int
        assertTrue(Math.abs(Color.red(c) - ((d shr 16) and 255)) <= 37)
        assertTrue(Math.abs(Color.green(c) - ((d shr 8) and 255)) <= 37)
        assertTrue(Math.abs(Color.blue(c) - (d and 255)) <= 43)
    }
}
