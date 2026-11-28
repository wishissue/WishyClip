package org.wishyclip.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the canvas limits that protect against out-of-memory crashes. */
class ProjectLimitsTest {

    @Test fun normalSizesAreUnchanged() {
        assertEquals(ProjectLimits.Size(1920, 1080), ProjectLimits.fit(1920, 1080))
        assertEquals(ProjectLimits.Size(3840, 2160), ProjectLimits.fit(3840, 2160))
    }

    @Test fun zeroAndNegativeAreRaisedToMinimum() {
        assertEquals(ProjectLimits.Size(64, 64), ProjectLimits.fit(0, -5))
    }

    @Test fun hugeCanvasIsScaledDownKeepingAspectAndStaysUnderPixelCap() {
        val s = ProjectLimits.fit(4096, 4096)
        assertTrue(s.width.toLong() * s.height <= ProjectLimits.MAX_PIXELS)
        assertEquals(s.width, s.height) // square stays square
        assertTrue(s.width > 2500)      // but is not shrunk needlessly
    }

    @Test fun extremeAspectRatiosNeverExceedCapOrGoBelowMinimum() {
        for ((w, h) in listOf(4096 to 64, 64 to 4096, 100000 to 100000, Int.MAX_VALUE to Int.MAX_VALUE, 4096 to 2160)) {
            val s = ProjectLimits.fit(w, h)
            assertTrue("$w x $h -> $s", s.width in ProjectLimits.MIN_SIDE..ProjectLimits.MAX_SIDE)
            assertTrue("$w x $h -> $s", s.height in ProjectLimits.MIN_SIDE..ProjectLimits.MAX_SIDE)
            assertTrue("$w x $h -> $s", s.width.toLong() * s.height <= ProjectLimits.MAX_PIXELS)
        }
    }

    @Test fun fpsAndNameAreSanitised() {
        assertEquals(1, ProjectLimits.cleanFps(0))
        assertEquals(60, ProjectLimits.cleanFps(1000))
        assertEquals("Untitled", ProjectLimits.cleanName("   "))
        assertEquals(ProjectLimits.MAX_NAME_LENGTH, ProjectLimits.cleanName("x".repeat(500)).length)
    }
}
