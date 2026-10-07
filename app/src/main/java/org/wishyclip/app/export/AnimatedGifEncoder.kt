package org.wishyclip.app.export

import android.graphics.Bitmap
import android.graphics.Color
import java.io.OutputStream

class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var delay = 100
    private var repeat = 0
    private var out: OutputStream? = null
    private var image: Bitmap? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 8
    private var colorTab: ByteArray? = null
    private var palSize = 7
    private var firstFrame = true

    fun setDelay(ms: Int) {
        delay = ms
    }

    fun setRepeat(iter: Int) {
        repeat = iter
    }

    fun start(os: OutputStream): Boolean {
        out = os
        return try {
            writeString("GIF89a")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addFrame(bmp: Bitmap): Boolean {
        if (out == null) return false
        image = bmp
        width = bmp.width
        height = bmp.height
        getImagePixels()
        analyzePixels()
        if (firstFrame) {
            writeLSD()
            writePalette()
            if (repeat >= 0) {
                writeNetscapeExt()
            }
        }
        writeGraphicCtrlExt()
        writeImageDesc()
        if (!firstFrame) {
            writePalette()
        }
        writePixels()
        firstFrame = false
        return true
    }

    fun finish(): Boolean {
        if (out == null) return false
        return try {
            out?.write(0x3B)
            out?.flush()
            out = null
            image = null
            pixels = null
            indexedPixels = null
            colorTab = null
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getImagePixels() {
        val w = width
        val h = height
        val intPixels = IntArray(w * h)
        image?.getPixels(intPixels, 0, w, 0, 0, w, h)
        pixels = ByteArray(w * h * 3)
        var count = 0
        for (i in intPixels.indices) {
            val color = intPixels[i]
            pixels!![count++] = (Color.red(color) and 0xFF).toByte()
            pixels!![count++] = (Color.green(color) and 0xFF).toByte()
            pixels!![count++] = (Color.blue(color) and 0xFF).toByte()
        }
    }

    private fun analyzePixels() {
        val nPix = pixels!!.size / 3
        indexedPixels = ByteArray(nPix)
        if (!buildExactPalette(nPix)) buildQuantizedPalette(nPix)
    }

    /** Uses the image's own colours when there are at most 256 of them. */
    private fun buildExactPalette(nPix: Int): Boolean {
        val tab = ByteArray(256 * 3)
        var palIdx = 0
        val map = HashMap<Int, Int>()
        for (i in 0 until nPix) {
            val r = pixels!![i * 3].toInt() and 0xFF
            val g = pixels!![i * 3 + 1].toInt() and 0xFF
            val b = pixels!![i * 3 + 2].toInt() and 0xFF
            val rgb = (r shl 16) or (g shl 8) or b
            var idx = map[rgb]
            if (idx == null) {
                if (palIdx >= 256) return false // too many colours: caller quantizes instead
                idx = palIdx
                map[rgb] = idx
                tab[palIdx * 3] = r.toByte()
                tab[palIdx * 3 + 1] = g.toByte()
                tab[palIdx * 3 + 2] = b.toByte()
                palIdx++
            }
            indexedPixels!![i] = idx.toByte()
        }
        colorTab = tab
        return true
    }

    /**
     * Anti-aliased drawings usually exceed 256 colours. Instead of mapping every extra colour to
     * palette entry 0 (which erased stroke edges), use a fixed 3-3-2 bit RGB palette.
     */
    private fun buildQuantizedPalette(nPix: Int) {
        val tab = ByteArray(256 * 3)
        for (i in 0 until 256) {
            val r = (i shr 5) and 7
            val g = (i shr 2) and 7
            val b = i and 3
            tab[i * 3] = (r * 255 / 7).toByte()
            tab[i * 3 + 1] = (g * 255 / 7).toByte()
            tab[i * 3 + 2] = (b * 255 / 3).toByte()
        }
        for (i in 0 until nPix) {
            val r = pixels!![i * 3].toInt() and 0xFF
            val g = pixels!![i * 3 + 1].toInt() and 0xFF
            val b = pixels!![i * 3 + 2].toInt() and 0xFF
            val rq = (r * 7 + 127) / 255
            val gq = (g * 7 + 127) / 255
            val bq = (b * 3 + 127) / 255
            indexedPixels!![i] = ((rq shl 5) or (gq shl 2) or bq).toByte()
        }
        colorTab = tab
    }

    private fun writeString(s: String) {
        for (i in s.indices) {
            out?.write(s[i].code)
        }
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out?.write(0x80 or 0x70 or palSize)
        out?.write(0)
        out?.write(0)
    }

    private fun writePalette() {
        out?.write(colorTab!!, 0, colorTab!!.size)
    }

    private fun writeNetscapeExt() {
        out?.write(0x21)
        out?.write(0xFF)
        out?.write(11)
        writeString("NETSCAPE2.0")
        out?.write(3)
        out?.write(1)
        writeShort(repeat)
        out?.write(0)
    }

    private fun writeGraphicCtrlExt() {
        out?.write(0x21)
        out?.write(0xF9)
        out?.write(4)
        out?.write(0)
        writeShort(((delay + 5) / 10).coerceAtLeast(2))
        out?.write(0)
        out?.write(0)
    }

    private fun writeImageDesc() {
        out?.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        if (firstFrame) {
            out?.write(0)
        } else {
            out?.write(0x80 or palSize)
        }
    }

    private fun writePixels() {
        val encoder = LzwEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write((value ushr 8) and 0xFF)
    }
}

class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val initCodeSize: Int
) {
    fun encode(os: OutputStream) {
        val minCodeSize = Math.max(2, initCodeSize)
        os.write(minCodeSize)

        val clearCode = 1 shl minCodeSize
        val eofCode = clearCode + 1
        val firstFree = clearCode + 2 // codes 0..eofCode are literals / control codes
        var curCode = firstFree

        val accum = ByteArray(256)
        var aCount = 0

        fun charOut(c: Byte) {
            accum[aCount++] = c
            if (aCount >= 254) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }

        fun flushChar() {
            if (aCount > 0) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }

        var curAccum = 0
        var curBits = 0

        fun output(code: Int, nBits: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += nBits
            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte())
                curAccum = curAccum ushr 8
                curBits -= 8
            }
        }

        var nBits = minCodeSize + 1
        output(clearCode, nBits)

        var ent = pixAry[0].toInt() and 0xFF
        val maxCode = 4096
        val hSize = 5003
        val hTab = IntArray(hSize) { -1 }
        val codTab = IntArray(hSize)

        for (i in 1 until pixAry.size) {
            val c = pixAry[i].toInt() and 0xFF
            val fCode = (c shl 12) or ent
            var hIdx = (c shl 4) xor ent
            if (hIdx >= hSize) hIdx -= hSize

            var found = false
            while (hTab[hIdx] != -1) {
                if (hTab[hIdx] == fCode) {
                    ent = codTab[hIdx]
                    found = true
                    break
                }
                hIdx = (hIdx + 1) % hSize
            }
            if (!found) {
                output(ent, nBits)
                ent = c
                if (curCode < maxCode) {
                    codTab[hIdx] = curCode++
                    hTab[hIdx] = fCode
                    // The decoder widens its codes once its table outgrows the current width.
                    if (curCode - 1 >= (1 shl nBits) && nBits < 12) nBits++
                } else {
                    hTab.fill(-1)
                    curCode = firstFree
                    output(clearCode, nBits)
                    nBits = minCodeSize + 1
                }
            }
        }
        output(ent, nBits)
        output(eofCode, nBits)

        if (curBits > 0) {
            charOut((curAccum and 0xFF).toByte())
        }
        flushChar()
        os.write(0)
    }
}
