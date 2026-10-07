package org.wishyclip.app.brush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTipParserTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun img(w: Int, h: Int, f: (Int, Int) -> Int) = RawImage(w, h, IntArray(w * h) { f(it % w, it / w) })
    private fun centre(b: ParsedBrush) = b.tip.alpha[4 * 9 + 4].toInt() and 255
    private fun inBox(x: Int, y: Int) = x in 3..5 && y in 3..5

    @Test fun darkShapeOnLightBackground() {
        val b = ImageTipParser.parse(ByteArray(1), "t") { img(9, 9) { x, y -> if (inBox(x, y)) black else white } }
        assertNotNull(b)
        assertEquals(255, centre(b!!))
        assertEquals(0, b.tip.alpha[0].toInt())
    }

    @Test fun lightShapeOnDarkBackground() {
        val b = ImageTipParser.parse(ByteArray(1), "t") { img(9, 9) { x, y -> if (inBox(x, y)) white else black } }
        assertEquals(255, centre(b!!))
        assertEquals(0, b.tip.alpha[0].toInt())
    }

    @Test fun transparentImageUsesAlpha() {
        val b = ImageTipParser.parse(ByteArray(1), "t") { img(9, 9) { x, y -> if (inBox(x, y)) 0xFF336699.toInt() else 0 } }
        assertEquals(255, centre(b!!))
    }

    @Test fun blankAndUndecodableAreRejected() {
        assertNull(ImageTipParser.parse(ByteArray(1), "t") { img(4, 4) { _, _ -> white } })
        assertNull(ImageTipParser.parse(ByteArray(1), "t") { null })
    }

    @Test fun importerAcceptsImageExtensions() {
        val decode: PngDecoder = { img(9, 9) { x, y -> if (inBox(x, y)) black else white } }
        for (name in listOf("Stamp.PNG", "a.jpg", "b.jpeg", "c.webp")) {
            val r = BrushImporter.parse(name, ByteArray(8), decode)
            assertNull(name, r.error)
            assertEquals(1, r.brushes.size)
        }
        assertTrue(BrushImporter.parse("x.txt", ByteArray(8), decode).error != null)
    }
}
