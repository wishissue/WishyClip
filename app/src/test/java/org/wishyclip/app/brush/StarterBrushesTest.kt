package org.wishyclip.app.brush

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterBrushesTest {
    private val brushes = StarterBrushes.all()

    @Test fun hasSixteenBrushesWithUniqueNames() {
        assertEquals(16, brushes.size)
        assertEquals(brushes.size, brushes.map { it.name.lowercase() }.toSet().size)
    }

    @Test fun everyBrushIsWithinEngineLimits() {
        for (b in brushes) {
            assertTrue("${b.name} spacing", b.spacing in BrushLimits.MIN_SPACING..BrushLimits.MAX_SPACING)
            assertTrue("${b.name} flow", b.flow in 0.05f..1f)
            assertTrue("${b.name} size", maxOf(b.tip.width, b.tip.height) <= BrushLimits.STORE_TIP_SIDE)
            assertEquals("${b.name} tip bytes", b.tip.width * b.tip.height, b.tip.alpha.size)
        }
    }

    @Test fun everyTipHasInkAndTransparentCorners() {
        for (b in brushes) {
            val a = b.tip.alpha
            assertTrue("${b.name} has ink", a.any { (it.toInt() and 255) > 0 })
            assertEquals("${b.name} corner", 0, a[0].toInt() and 255)
            assertEquals("${b.name} corner", 0, a[a.size - 1].toInt() and 255)
        }
    }

    @Test fun technicalPenIsSolidAtTheCentre() {
        val pen = brushes.first { it.name == "Technical pen" }
        val c = pen.tip.width / 2
        assertEquals(255, pen.tip.alpha[c * pen.tip.width + c].toInt() and 255)
    }

    @Test fun packIsDeterministic() {
        val again = StarterBrushes.all()
        for (i in brushes.indices) assertArrayEquals(brushes[i].tip.alpha, again[i].tip.alpha)
    }

    @Test fun grainyBrushesAreNotSolid() {
        val hb = brushes.first { it.name == "Graphite HB" }
        val distinct = hb.tip.alpha.map { it.toInt() and 255 }.toSet().size
        assertTrue("HB should have many grey levels, had $distinct", distinct > 20)
    }
}
