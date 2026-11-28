package org.wishyclip.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifQuantizerTest {
    private fun rgbOf(colors: List<Triple<Int, Int, Int>>): ByteArray {
        val a = ByteArray(colors.size * 3)
        colors.forEachIndexed { i, c -> a[i * 3] = c.first.toByte(); a[i * 3 + 1] = c.second.toByte(); a[i * 3 + 2] = c.third.toByte() }
        return a
    }

    @Test fun manyColoursMapToAPaletteThatStaysClose() {
        val colors = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0 until 90) for (x in 0 until 120) colors.add(Triple(x * 2 % 256, y * 3 % 256, (x + y) % 256))
        val r = GifQuantizer.quantize(rgbOf(colors), colors.size)
        assertEquals(256 * 3, r.palette.size)
        assertEquals(colors.size, r.indexes.size)
        var sum = 0L
        colors.forEachIndexed { i, c ->
            val k = r.indexes[i].toInt() and 0xFF
            val dr = Math.abs(c.first - (r.palette[k * 3].toInt() and 0xFF))
            val dg = Math.abs(c.second - (r.palette[k * 3 + 1].toInt() and 0xFF))
            val db = Math.abs(c.third - (r.palette[k * 3 + 2].toInt() and 0xFF))
            sum += maxOf(dr, dg, db)
        }
        // Measured about 7 on this image with a Python port of the same algorithm; the old fixed 3-3-2 palette was far worse.
        assertTrue("mean error ${sum / colors.size}", sum / colors.size < 16)
    }

    @Test fun whiteStaysNearWhite() {
        val colors = ArrayList<Triple<Int, Int, Int>>()
        for (i in 0 until 2000) colors.add(Triple(i % 256, (i * 7) % 256, (i * 13) % 256))
        colors.add(Triple(0, 0, 0)); colors.add(Triple(255, 255, 255))
        val r = GifQuantizer.quantize(rgbOf(colors), colors.size)
        val w = r.indexes[colors.size - 1].toInt() and 0xFF
        assertTrue((r.palette[w * 3].toInt() and 0xFF) >= 225)
        assertTrue((r.palette[w * 3 + 2].toInt() and 0xFF) >= 225)
    }

    @Test fun singleColourImageWorks() {
        val colors = List(50) { Triple(10, 20, 30) }
        val r = GifQuantizer.quantize(rgbOf(colors), colors.size)
        assertEquals(1, r.indexes.map { it.toInt() }.toSet().size)
    }
}
