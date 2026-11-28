package org.wishyclip.app.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoupeLayoutTest {
    @Test fun penFarFromTopLeftKeepsLoupeLeft() {
        assertTrue(LoupeLayout.useLeftCorner(900f, 900f, 300f, 30f))
        assertTrue(LoupeLayout.useLeftCorner(1000f, 100f, 300f, 30f)) // top-right pen: left is free
    }

    @Test fun penNearTopLeftMovesLoupeRight() {
        assertFalse(LoupeLayout.useLeftCorner(150f, 150f, 300f, 30f))
        assertFalse(LoupeLayout.useLeftCorner(300f, 200f, 300f, 30f))
    }
}
