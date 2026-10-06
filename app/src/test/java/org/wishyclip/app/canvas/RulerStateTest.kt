package org.wishyclip.app.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class RulerStateTest {
    private val r = RulerState(0f, 0f, 100f, 0f)

    @Test
    fun projectsOntoTheSegmentAndClampsAtTheEnds() {
        val o = FloatArray(2)
        r.project(50f, 30f, o); assertEquals(50f, o[0], 1e-3f); assertEquals(0f, o[1], 1e-3f)
        r.project(-20f, 5f, o); assertEquals(0f, o[0], 1e-3f)
        r.project(250f, -9f, o); assertEquals(100f, o[0], 1e-3f)
    }

    @Test
    fun hitTestPrefersEndsAndFindsCentre() {
        assertEquals(RulerHandle.A, r.hit(3f, 2f, 10f))
        assertEquals(RulerHandle.B, r.hit(98f, 1f, 10f))
        assertEquals(RulerHandle.CENTER, r.hit(52f, 3f, 10f))
        assertEquals(RulerHandle.NONE, r.hit(25f, 40f, 10f))
        assertEquals(RulerHandle.A, RulerState(0f, 0f, 8f, 0f).hit(4f, 0f, 10f))
    }

    @Test
    fun draggingMovesOrRotates() {
        assertEquals(RulerState(5f, 7f, 105f, 7f), r.drag(RulerHandle.CENTER, 5f, 7f))
        assertEquals(50f, r.drag(RulerHandle.B, 0f, 50f).by, 1e-3f)
        assertEquals(0f, r.drag(RulerHandle.B, 0f, 50f).ay, 1e-3f)
    }

    @Test
    fun degenerateRulerIsSafe() {
        val o = FloatArray(2)
        RulerState(10f, 10f, 10f, 10f).project(99f, 99f, o)
        assertEquals(10f, o[0], 1e-3f)
    }
}
