package org.wishyclip.app.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasViewStateTest {
    @Test
    fun rotateBy90RotatesPanWithIt() {
        val v = CanvasViewState(pan = Offset(10f, 0f))
        v.rotateBy(90f)
        assertEquals(90f, v.rotation, 1e-3f)
        assertEquals(0f, v.pan.x, 1e-3f)
        assertEquals(10f, v.pan.y, 1e-3f)
    }

    @Test
    fun rotationStaysInRange() {
        val v = CanvasViewState()
        repeat(5) { v.rotateBy(90f) }
        assertEquals(90f, v.rotation, 1e-3f)
        v.rotateBy(-270f)
        assertTrue(v.rotation > -180f && v.rotation <= 180f)
    }

    @Test
    fun resetClearsTheTransform() {
        val v = CanvasViewState(2f, Offset(5f, 5f), 45f)
        assertTrue(v.isTransformed)
        v.reset()
        assertFalse(v.isTransformed)
    }

    @Test
    fun rescalePanIgnoresBadFactors() {
        val v = CanvasViewState(pan = Offset(10f, 20f))
        v.rescalePan(Float.NaN); v.rescalePan(0f); v.rescalePan(-1f)
        assertEquals(10f, v.pan.x, 1e-3f)
        v.rescalePan(2f)
        assertEquals(40f, v.pan.y, 1e-3f)
    }
}
