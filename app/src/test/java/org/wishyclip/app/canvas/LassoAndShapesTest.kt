package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wishyclip.app.model.Tool

@RunWith(RobolectricTestRunner::class)
class LassoAndShapesTest {

    @Test
    fun testLassoSelectionExtraction() {
        val layerBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        layerBmp.eraseColor(Color.RED)

        val tool = StandardLassoTool()
        tool.begin(10f, 10f)
        tool.addPoint(50f, 10f)
        tool.addPoint(50f, 50f)
        tool.addPoint(10f, 50f)

        val selection = tool.end(layerBmp)
        assertNotNull(selection)
        assertEquals(40f, selection!!.bounds.width(), 0.1f)
        assertEquals(40f, selection.bounds.height(), 0.1f)

        // Floating bitmap should exist
        assertNotNull(selection.pixels)

        // Testing transform matrix
        selection.translateX = 10f
        selection.translateY = 20f
        selection.scaleX = 1.5f
        selection.rotation = 45f

        val matrix = selection.getMatrix()
        assertNotNull(matrix)
    }

    @Test
    fun testShapeRenderer() {
        val targetBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val renderer = StrokeRenderer()
        renderer.begin(targetBmp, Tool.LINE, Color.BLUE, 5f, 1f, 10f, 10f)
        renderer.moveTo(80f, 80f)

        val patch = renderer.finish()
        assertNotNull(patch)
        renderer.release()
    }
}
