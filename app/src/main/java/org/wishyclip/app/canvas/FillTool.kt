package org.wishyclip.app.canvas

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Scanline flood fill with tolerance running off the main thread.
 */
interface FillTool {
    /** @return true if any pixel changed. */
    fun fill(target: Bitmap, x: Int, y: Int, argb: Int, tolerance: Int): Boolean
}

class ScanlineFillTool : FillTool {
    override fun fill(target: Bitmap, x: Int, y: Int, argb: Int, tolerance: Int): Boolean {
        val width = target.width
        val height = target.height
        val startX = x
        val startY = y
        if (startX !in 0 until width || startY !in 0 until height) return false

        val pixels = IntArray(width * height)
        target.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetIndex = startY * width + startX
        val targetColor = pixels[targetIndex]

        if (colorMatches(targetColor, argb, 0)) return false

        val visited = BooleanArray(width * height)
        val stackX = IntArray(width * height)
        val stackY = IntArray(width * height)
        var stackSize = 0

        stackX[stackSize] = startX
        stackY[stackSize] = startY
        stackSize++

        var changed = false

        while (stackSize > 0) {
            stackSize--
            val currX = stackX[stackSize]
            val currY = stackY[stackSize]

            var left = currX
            while (left >= 0) {
                val idx = currY * width + left
                if (visited[idx] || !colorMatches(pixels[idx], targetColor, tolerance)) break
                left--
            }
            left++

            var right = currX
            while (right < width) {
                val idx = currY * width + right
                if (visited[idx] || !colorMatches(pixels[idx], targetColor, tolerance)) break
                right++
            }
            right--

            for (i in left..right) {
                val idx = currY * width + i
                pixels[idx] = argb
                visited[idx] = true
                changed = true
            }

            for (nextY in intArrayOf(currY - 1, currY + 1)) {
                if (nextY in 0 until height) {
                    var inSpan = false
                    for (i in left..right) {
                        val idx = nextY * width + i
                        val matches = !visited[idx] && colorMatches(pixels[idx], targetColor, tolerance)
                        if (!inSpan && matches) {
                            if (stackSize < stackX.size) {
                                stackX[stackSize] = i
                                stackY[stackSize] = nextY
                                stackSize++
                            }
                            inSpan = true
                        } else if (inSpan && !matches) {
                            inSpan = false
                        }
                    }
                }
            }
        }

        if (changed) {
            target.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return changed
    }

    private fun colorMatches(c1: Int, c2: Int, tolerance: Int): Boolean {
        if (c1 == c2) return true
        if (tolerance <= 0) return false
        val a1 = (c1 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 ushr 24) and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        return abs(a1 - a2) <= tolerance &&
                abs(r1 - r2) <= tolerance &&
                abs(g1 - g2) <= tolerance &&
                abs(b1 - b2) <= tolerance
    }
}
