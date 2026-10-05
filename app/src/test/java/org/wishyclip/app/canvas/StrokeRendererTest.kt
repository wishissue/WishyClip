package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.wishyclip.app.model.Tool

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokeRendererTest {

    private fun blank(w: Int = 300, h: Int = 200) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun brushStrokeStaysOffTheLayerUntilFinished() {
        val layer = blank()
        val r = StrokeRenderer()
        r.begin(layer, Tool.PEN, Color.RED, 12f, 1f, 20f, 100f)
        for (x in 30..250 step 10) r.moveTo(x.toFloat(), 100f)

        // Live ink is on the overlay, the layer itself is untouched until finish().
        assertEquals(0, Color.alpha(layer.getPixel(120, 100)))
        assertNotNull(r.overlay())

        val patch = r.finish()
        assertNotNull(patch)
        assertTrue(Color.alpha(layer.getPixel(120, 100)) > 200)
        assertNull(r.overlay())
        r.release()
    }

    @Test
    fun undoPatchRestoresOnlyTheTouchedRect() {
        val layer = blank()
        layer.eraseColor(Color.WHITE)
        val r = StrokeRenderer()
        r.begin(layer, Tool.PEN, Color.BLACK, 10f, 1f, 50f, 50f)
        r.moveTo(150f, 60f)
        val patch = r.finish()!!

        // Patch is a small region, not the full 300x200 canvas.
        assertTrue(patch.bitmap.width < layer.width)
        assertEquals(patch.rect.width(), patch.bitmap.width)

        BitmapOps.putAt(layer, patch.bitmap, patch.rect.left, patch.rect.top)
        assertEquals(Color.WHITE, layer.getPixel(100, 55))
        r.release()
    }

    @Test
    fun translucentBrushDoesNotDarkenWhereItOverlapsItself() {
        val layer = blank()
        val r = StrokeRenderer()
        r.begin(layer, Tool.HIGHLIGHTER, Color.YELLOW, 10f, 1f, 50f, 100f)
        r.moveTo(150f, 100f)
        r.moveTo(50f, 101f) // doubles back over the same area
        r.finish()
        val a = Color.alpha(layer.getPixel(100, 100))
        // Highlighter alpha factor is 0.4 -> ~102, never accumulating towards opaque.
        assertTrue("alpha was $a", a in 90..115)
        r.release()
    }

    @Test
    fun eraserClearsDirectlyAndPatchRestoresOriginal() {
        val layer = blank()
        layer.eraseColor(Color.RED)
        val r = StrokeRenderer()
        r.begin(layer, Tool.ERASER, Color.BLACK, 10f, 1f, 40f, 100f)
        r.moveTo(200f, 100f)
        assertEquals(0, Color.alpha(layer.getPixel(100, 100)))

        val patch = r.finish()!!
        BitmapOps.putAt(layer, patch.bitmap, patch.rect.left, patch.rect.top)
        assertEquals(Color.RED, layer.getPixel(100, 100))
        r.release()
    }

    @Test
    fun cancelRestoresEraserAndClearsBrush() {
        val layer = blank()
        layer.eraseColor(Color.BLUE)
        val r = StrokeRenderer()
        r.begin(layer, Tool.ERASER, Color.BLACK, 10f, 1f, 40f, 100f)
        r.moveTo(200f, 100f)
        r.cancel()
        assertEquals(Color.BLUE, layer.getPixel(100, 100))
        assertTrue(!r.active)

        r.begin(layer, Tool.PEN, Color.RED, 10f, 1f, 10f, 10f)
        r.moveTo(100f, 10f)
        r.cancel()
        assertEquals(Color.BLUE, layer.getPixel(50, 10))
        r.release()
    }

    @Test
    fun shapePreviewOnlyDirtiesItsOwnRect() {
        val layer = blank()
        val r = StrokeRenderer()
        r.begin(layer, Tool.RECT, Color.GREEN, 4f, 1f, 20f, 20f)
        r.moveTo(60f, 60f)
        r.moveTo(100f, 80f) // dragging resizes the preview, stale pixels must go
        val d = r.overlay()!!.dirty
        assertTrue(d.right < layer.width && d.bottom < layer.height)
        assertNotNull(r.finish())
        r.release()
    }

    @Test
    fun lassoLiftCancelAndSelectionHitTest() {
        val layer = blank(100, 100)
        layer.eraseColor(Color.RED)
        val tool = StandardLassoTool()
        tool.begin(20f, 20f); tool.addPoint(60f, 20f); tool.addPoint(60f, 60f); tool.addPoint(20f, 60f)
        val sel = tool.end(layer)!!

        // Lifted region is cleared on the layer and restorable from liftBefore.
        assertEquals(0, Color.alpha(layer.getPixel(40, 40)))
        BitmapOps.putAt(layer, sel.liftBefore!!, sel.liftRect!!.left, sel.liftRect!!.top)
        assertEquals(Color.RED, layer.getPixel(40, 40))

        assertEquals(SelectionHit.MOVE, sel.hitTest(40f, 40f, 10f, 20f))
        assertEquals(SelectionHit.SCALE, sel.hitTest(60f, 60f, 10f, 20f))
        assertEquals(SelectionHit.NONE, sel.hitTest(95f, 95f, 10f, 20f))
        sel.release()
    }

    @Test
    fun textRasterIsNonEmptyAndEditable() {
        val bmp = TextRaster.render("Hi\nthere", Color.BLACK, 40f, 1f)
        assertTrue(bmp.width > 1 && bmp.height > 40)
        val spec = TextSpec("Hi", 40f)
        val sel = LassoSelection(bmp, android.graphics.RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), android.graphics.Path(), text = spec)
        assertTrue(sel.isText)
    }
}
