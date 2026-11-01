package org.wishyclip.app.canvas

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LayerDataTest {

    private var allocations = 0

    private fun emptyLayer(id: Long = 1L, w: Int = 10, h: Int = 10) = LayerData(
        id = id, name = "L$id", visible = true, opacity = 1f, bitmap = null,
        allocator = { allocations++; Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    )

    @Test fun emptyLayerHoldsNoPixels() {
        val layer = emptyLayer()
        assertFalse(layer.allocated)
        assertNull(layer.peekBitmap())
        assertEquals(0L, layer.byteCount)
        assertEquals(0, allocations)
    }

    @Test fun firstWriteAllocatesOnceAndReusesTheBitmap() {
        val layer = emptyLayer()
        val first = layer.bitmap
        val second = layer.bitmap
        assertSame(first, second)
        assertEquals(1, allocations)
        assertTrue(layer.allocated)
        assertEquals(10L * 10L * 4L, layer.byteCount)
    }

    @Test fun releaseReturnsLayerToEmpty() {
        val layer = emptyLayer()
        val bmp = layer.bitmap
        layer.release()
        assertTrue(bmp.isRecycled)
        assertFalse(layer.allocated)
        assertEquals(0L, layer.byteCount)
        // Writing again gives a fresh, usable bitmap.
        assertFalse(layer.bitmap.isRecycled)
        assertEquals(2, allocations)
    }

    @Test fun layerWithoutPixelsOrAllocatorFailsLoudly() {
        val layer = LayerData(1L, "x", true, 1f, null)
        try {
            layer.bitmap
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test fun deepCopyOfEmptyLayerStaysEmpty() {
        val copy = emptyLayer().deepCopy()
        assertFalse(copy.allocated)
        assertEquals(0, allocations)
    }

    @Test fun deepCopyOfPaintedLayerIsIndependent() {
        val layer = emptyLayer()
        layer.bitmap.eraseColor(0xFFFF0000.toInt())
        val copy = layer.deepCopy()
        assertNotSame(layer.bitmap, copy.bitmap)
        layer.bitmap.eraseColor(0xFF00FF00.toInt())
        assertEquals(0xFFFF0000.toInt(), copy.bitmap.getPixel(0, 0))
    }

    @Test fun twentyOneEmptyLayersCostNothing() {
        // The scenario from the user review: 21 layers, none drawn on yet, at 1080p.
        val layers = (1L..21L).map { emptyLayer(it, 1920, 1080) }
        val frame = FrameData(1L, layers.toMutableList())
        assertEquals(0L, frame.allocatedBytes())
        assertEquals(0, allocations)
    }

    @Test fun frameBytesCountOnlyPaintedLayers() {
        val frame = FrameData(1L, (1L..5L).map { emptyLayer(it) }.toMutableList())
        frame.layers[2].bitmap // paint-equivalent: first write allocates
        assertEquals(10L * 10L * 4L, frame.allocatedBytes())
        frame.release()
        assertEquals(0L, frame.allocatedBytes())
    }

    @Test fun legacyConstructorWithBitmapStillWorks() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val layer = LayerData(id = 1L, name = "L", visible = true, opacity = 1f, bitmap = bmp)
        assertTrue(layer.allocated)
        assertNotNull(layer.peekBitmap())
        assertSame(bmp, layer.bitmap)
    }
}
