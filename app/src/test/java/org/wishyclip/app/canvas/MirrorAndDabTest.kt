package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wishyclip.app.model.MirrorMode
import org.wishyclip.app.model.Tool
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MirrorAndDabTest {

    private fun blank(w: Int = 300, h: Int = 200) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private fun stroke(layer: Bitmap, tool: Tool, mirror: MirrorMode, dab: DabBrush? = null) {
        val r = StrokeRenderer()
        r.begin(layer, tool, Color.BLACK, 10f, 1f, 40f, 50f, 1f, 0f, mirror, dab)
        for (x in 50..100 step 10) r.moveTo(x.toFloat(), 50f)
        assertNotNull(r.finish())
        r.release()
    }

    private fun inked(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y)) > 150

    @Test
    fun noMirrorLeavesTheOtherSideEmpty() {
        val layer = blank()
        stroke(layer, Tool.PEN, MirrorMode.OFF)
        assertTrue(inked(layer, 70, 50))
        assertTrue(!inked(layer, 300 - 70, 50))
    }

    @Test
    fun leftRightMirrorsAcrossTheVerticalCentreLine() {
        val layer = blank()
        stroke(layer, Tool.PEN, MirrorMode.LEFT_RIGHT)
        assertTrue(inked(layer, 70, 50))
        assertTrue(inked(layer, 300 - 70, 50))
        assertTrue(!inked(layer, 70, 200 - 50))
    }

    @Test
    fun fourWayMirrorsIntoAllQuadrants() {
        val layer = blank()
        stroke(layer, Tool.PEN, MirrorMode.FOUR_WAY)
        assertTrue(inked(layer, 70, 50))
        assertTrue(inked(layer, 230, 50))
        assertTrue(inked(layer, 70, 150))
        assertTrue(inked(layer, 230, 150))
    }

    @Test
    fun mirroredEraserErasesBothSides() {
        val layer = blank()
        layer.eraseColor(Color.BLACK)
        val r = StrokeRenderer()
        r.begin(layer, Tool.ERASER, Color.BLACK, 14f, 1f, 40f, 50f, 1f, 0f, MirrorMode.LEFT_RIGHT, null)
        for (x in 50..100 step 10) r.moveTo(x.toFloat(), 50f)
        val patch = r.finish()
        assertNotNull(patch)
        assertEquals(0, Color.alpha(layer.getPixel(70, 50)))
        assertEquals(0, Color.alpha(layer.getPixel(230, 50)))
        assertEquals(255, Color.alpha(layer.getPixel(150, 150)))
        r.release()
    }

    @Test
    fun mirroredShapeIsDrawnOnBothSides() {
        val layer = blank()
        val r = StrokeRenderer()
        r.begin(layer, Tool.RECT, Color.BLACK, 6f, 1f, 30f, 30f, 1f, 0f, MirrorMode.LEFT_RIGHT, null)
        r.moveTo(90f, 80f)
        assertNotNull(r.finish())
        assertTrue(inked(layer, 30, 55))
        assertTrue(inked(layer, 270, 55))
        r.release()
    }

    private fun squareTip(side: Int = 16): DabBrush {
        val tip = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
        tip.copyPixelsFromBuffer(ByteBuffer.wrap(ByteArray(side * side) { 255.toByte() }))
        return DabBrush(tip, spacing = 0.25f, angle = 0f, rotateWithStroke = false, scatter = 0f, sizeJitter = 0f, flow = 1f)
    }

    @Test
    fun dabBrushStampsTheTipAlongTheStroke() {
        val layer = blank()
        stroke(layer, Tool.CUSTOM, MirrorMode.OFF, squareTip())
        assertTrue(inked(layer, 40, 50))
        assertTrue(inked(layer, 75, 50))
        assertTrue(inked(layer, 100, 50))
        assertTrue(!inked(layer, 70, 90))
    }

    @Test
    fun dabBrushHonoursMirrorToo() {
        val layer = blank()
        stroke(layer, Tool.CUSTOM, MirrorMode.LEFT_RIGHT, squareTip())
        assertTrue(inked(layer, 75, 50))
        assertTrue(inked(layer, 300 - 75, 50))
    }

    @Test
    fun customToolWithoutATipFallsBackToAPlainLine() {
        val layer = blank()
        stroke(layer, Tool.CUSTOM, MirrorMode.OFF, dab = null)
        assertTrue(inked(layer, 75, 50))
    }
}
