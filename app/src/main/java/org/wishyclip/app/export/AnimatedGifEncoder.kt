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
     * Anti-aliased drawings usually exceed 256 colours. A palette is chosen per frame by median cut
     * (see [GifQuantizer]), which follows the colours the picture really uses.
     */
    private fun buildQuantizedPalette(nPix: Int) {
        val r = GifQuantizer.quantize(pixels!!, nPix)
        colorTab = r.palette
        indexedPixels = r.indexes
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

/**
 * Median-cut colour quantizer. Colours are first reduced to 5 bits per channel (32768 bins), then the bin
 * set is split repeatedly along its widest channel at the pixel-count median until there are 256 boxes.
 * Each box becomes one palette colour (the pixel-weighted average). Pure Kotlin so it is unit-tested.
 */
internal object GifQuantizer {
    class Result(val palette: ByteArray, val indexes: ByteArray)

    private fun binOf(rgb: ByteArray, i: Int): Int {
        val r = rgb[i * 3].toInt() and 0xFF
        val g = rgb[i * 3 + 1].toInt() and 0xFF
        val b = rgb[i * 3 + 2].toInt() and 0xFF
        return ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
    }

    private fun channel(bin: Int, c: Int): Int = when (c) {
        0 -> bin shr 10
        1 -> (bin shr 5) and 31
        else -> bin and 31
    }

    /** [rgb] holds r, g, b bytes per pixel. Always returns a 256-entry palette and one index per pixel. */
    fun quantize(rgb: ByteArray, nPix: Int): Result {
        val hist = IntArray(32768)
        for (i in 0 until nPix) hist[binOf(rgb, i)]++
        val bins = IntArray(32768)
        var nb = 0
        for (b in 0 until 32768) if (hist[b] > 0) bins[nb++] = b

        val start = IntArray(256)
        val end = IntArray(256)
        var count = 1
        end[0] = nb
        while (count < 256) {
            var best = -1
            var bestPix = -1L
            for (k in 0 until count) {
                if (end[k] - start[k] < 2) continue
                var pix = 0L
                for (j in start[k] until end[k]) pix += hist[bins[j]]
                if (pix > bestPix) { bestPix = pix; best = k }
            }
            if (best < 0) break
            val s = start[best]
            val e = end[best]
            var widest = 0
            var widestExtent = -1
            for (c in 0..2) {
                var lo = 31
                var hi = 0
                for (j in s until e) {
                    val v = channel(bins[j], c)
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                if (hi - lo > widestExtent) { widestExtent = hi - lo; widest = c }
            }
            val keys = LongArray(e - s) { (channel(bins[s + it], widest).toLong() shl 20) or bins[s + it].toLong() }
            keys.sort()
            for (j in keys.indices) bins[s + j] = (keys[j] and 0xFFFFF).toInt()
            var acc = 0L
            var m = 1
            for (j in s until e) {
                acc += hist[bins[j]]
                if (acc * 2 >= bestPix) { m = j - s + 1; break }
            }
            m = m.coerceIn(1, e - s - 1)
            start[count] = s + m
            end[count] = e
            end[best] = s + m
            count++
        }

        val palette = ByteArray(256 * 3)
        val binToIndex = IntArray(32768)
        for (k in 0 until count) {
            var w = 0L
            var sr = 0L
            var sg = 0L
            var sb = 0L
            for (j in start[k] until end[k]) {
                val b = bins[j]
                val n = hist[b].toLong()
                w += n
                sr += n * (channel(b, 0) * 255 / 31)
                sg += n * (channel(b, 1) * 255 / 31)
                sb += n * (channel(b, 2) * 255 / 31)
                binToIndex[b] = k
            }
            if (w > 0) {
                palette[k * 3] = (sr / w).toInt().toByte()
                palette[k * 3 + 1] = (sg / w).toInt().toByte()
                palette[k * 3 + 2] = (sb / w).toInt().toByte()
            }
        }
        val indexes = ByteArray(nPix)
        for (i in 0 until nPix) indexes[i] = binToIndex[binOf(rgb, i)].toByte()
        return Result(palette, indexes)
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
