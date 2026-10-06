package org.wishyclip.app.brush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Brush file parsers, exercised with synthetic files built here from each format's layout.
 * (Real-world brush packs should still be tried by hand: these only prove the parsers agree with
 * the documented structure.)
 */
class BrushImportTest {

    @get:Rule
    val tmp = TemporaryFolder()


    private fun inkCount(m: TipMask) = m.alpha.count { (it.toInt() and 255) > 128 }
    private fun centerInk(m: TipMask) = m.alpha[(m.height / 2) * m.width + m.width / 2].toInt() and 255
    private fun cornerInk(m: TipMask) = m.alpha[0].toInt() and 255

    private val g = gbr(16, 16, 1, "Round16", 25) { x, y -> intArrayOf(if (Math.hypot(x - 7.5, y - 7.5) < 6) 0 else 255) }

    @Test fun gbrGreyscaleBlackIsInk() {
        val b = GbrParser.parse(g)!!
        assertEquals("Round16", b.name)
        assertEquals(0.25f, b.spacing, 1e-4f)
        assertTrue(centerInk(b.tip) > 200); assertEquals(0, cornerInk(b.tip))
    }

    @Test fun gbrRgbaUsesAlpha() {
        val b = GbrParser.parse(gbr(8, 8, 4, "C", 10) { x, y -> intArrayOf(200, 10, 10, if (x in 2..5 && y in 2..5) 255 else 0) })!!
        assertEquals(255, centerInk(b.tip)); assertEquals(0, cornerInk(b.tip))
    }

    @Test fun gbrRejectsBadInput() {
        assertNull(GbrParser.parse(g.copyOf(40)))
        assertNull(GbrParser.parse(ByteArray(100) { it.toByte() }))
    }

    @Test fun gihTakesFirstBrush() {
        val b = GbrParser.parseGih("Pipe\n1 ncells:1 placement:default\n".toByteArray() + g)!!
        assertTrue(centerInk(b.tip) > 200)
    }

    private fun abrV2(compression: Int): ByteArray {
        val w = 10; val h = 10
        val rows = Array(h) { y -> ByteArray(w) { x -> if (Math.hypot(x - 4.5, y - 4.5) < 4) 255.toByte() else 0 } }
        val body = ByteArrayOutputStream()
        be32(body, 0); be16(body, 25)
        be32(body, 5); body.write("Soft\u0000".toByteArray(Charsets.UTF_16BE))
        body.write(1)
        for (i in 0 until 4) be16(body, 0)
        be32(body, 0); be32(body, 0); be32(body, h); be32(body, w)
        be16(body, 8); body.write(compression)
        if (compression == 0) {
            for (r in rows) body.write(r)
        } else {
            val p = rows.map { packBits(it) }
            for (x in p) be16(body, x.size)
            for (x in p) body.write(x)
        }
        val o = ByteArrayOutputStream()
        be16(o, 2); be16(o, 2)
        be16(o, 1); be32(o, 4); be32(o, 0)
        be16(o, 2); be32(o, body.size()); o.write(body.toByteArray())
        return o.toByteArray()
    }

    @Test fun abrV2RawAndRle() {
        for (c in 0..1) {
            val a = AbrParser.parse(abrV2(c))
            assertEquals(1, a.size)
            assertEquals(255, centerInk(a[0].tip)); assertEquals(0, cornerInk(a[0].tip))
            assertEquals(0.25f, a[0].spacing, 1e-4f)
        }
    }

    @Test fun abrV6SampledTip() {
        val w = 12; val h = 12
        val rec = ByteArrayOutputStream()
        rec.write(36); rec.write("12345678-1234-1234-1234-123456789012".toByteArray())
        for (i in 0 until 40) rec.write(i * 7)
        be32(rec, 0); be32(rec, 0); be32(rec, h); be32(rec, w); be16(rec, 8); rec.write(1)
        val rows = Array(h) { y -> ByteArray(w) { x -> if (Math.hypot(x - 5.5, y - 5.5) < 5) 255.toByte() else 0 } }
        val p = rows.map { packBits(it) }
        for (x in p) be16(rec, x.size); for (x in p) rec.write(x)
        val samp = ByteArrayOutputStream(); be32(samp, rec.size()); samp.write(rec.toByteArray())
        while (samp.size() % 4 != 0) samp.write(0)
        val o = ByteArrayOutputStream(); be16(o, 6); be16(o, 1)
        o.write("8BIM".toByteArray()); o.write("samp".toByteArray()); be32(o, samp.size()); o.write(samp.toByteArray())
        val a = AbrParser.parse(o.toByteArray())
        assertEquals(1, a.size); assertEquals(255, centerInk(a[0].tip))
    }

    @Test fun abrGarbageAndTruncated() {
        assertTrue(AbrParser.parse(ByteArray(64) { 7 }).isEmpty())
        assertTrue(AbrParser.parse(abrV2(1).copyOf(30)).isEmpty())
    }

    @Test fun wbrushRoundTripsMetadata() {
        val json = "{\"name\":\"My \\\"Star\\\" brush\",\"spacing\":0.3,\"angle\":45,\"rotateWithStroke\":true,\"scatter\":0.5,\"sizeJitter\":0.2,\"flow\":0.6}"
        val wb = zip("brush.json" to json.toByteArray(), "tip.png" to roundPng(32, 32, true, true))
        val r = BrushImporter.parse("star.wbrush", wb, decoder)
        val b = r.brushes.single()
        assertEquals("My \"Star\" brush", b.name)
        assertEquals(45f, b.angle, 0f); assertTrue(b.rotateWithStroke)
        assertEquals(0.3f, b.spacing, 1e-4f); assertEquals(0.6f, b.flow, 1e-4f); assertEquals(0.5f, b.scatter, 1e-4f)
        assertEquals(255, centerInk(b.tip)); assertEquals(0, cornerInk(b.tip))
    }

    @Test fun wbrushOpaquePngBlackIsInk() {
        val b = BrushImporter.parse("x.wbrush", zip("tip.png" to roundPng(20, 20, false, true)), decoder).brushes.single()
        assertTrue(centerInk(b.tip) > 200); assertEquals(0, cornerInk(b.tip))
    }

    @Test fun wbrushWithoutTipFailsCleanly() {
        assertNotNull(BrushImporter.parse("x.wbrush", zip("brush.json" to "{}".toByteArray()), decoder).error)
    }

    private val kppXml = "<Preset paintopid=\"paintbrush\" name=\"Ink Pen\"><param name=\"brush_definition\" type=\"string\">" +
        "&lt;Brush type=&quot;png_brush&quot; spacing=&quot;0.35&quot; angle=&quot;10&quot; /&gt;</param>" +
        "<resources><resource name=\"tip.gbr\" type=\"brushes\" md5sum=\"x\">" +
        java.util.Base64.getMimeEncoder().encodeToString(g) + "</resource></resources></Preset>"

    @Test fun kritaPresetWithEmbeddedTip() {
        val kpp = pngWithText(roundPng(8, 8, true, true), "preset", kppXml)
        val b = BrushImporter.parse("pen.kpp", kpp, decoder).brushes.single()
        assertEquals("Ink Pen", b.name); assertEquals(0.35f, b.spacing, 1e-4f); assertEquals(10f, b.angle, 0f)
        assertEquals(16, b.tip.width); assertTrue(centerInk(b.tip) > 200)
    }

    @Test fun kritaAutoBrushFallsBackToRoundTip() {
        val kpp = pngWithText(roundPng(8, 8, true, true), "preset", "<Preset name=\"Auto\"><Brush type=\"auto_brush\" spacing=\"0.1\"/></Preset>")
        val b = BrushImporter.parse("a.kpp", kpp, decoder).brushes.single()
        assertEquals(128, b.tip.width); assertEquals(255, centerInk(b.tip)); assertEquals(0, cornerInk(b.tip))
    }

    @Test fun kritaBundleAndTipOnlyBundle() {
        val kpp = pngWithText(roundPng(8, 8, true, true), "preset", kppXml)
        assertEquals(2, BrushImporter.parse("p.bundle", zip("paintoppresets/A.kpp" to kpp, "paintoppresets/B.kpp" to kpp), decoder).brushes.size)
        assertEquals(2, BrushImporter.parse("t.bundle", zip("brushes/r.gbr" to g, "brushes/d.png" to roundPng(16, 16, true, true)), decoder).brushes.size)
    }

    @Test fun procreateBrushAndBrushset() {
        val shape = roundPng(24, 24, false, false)
        val b = BrushImporter.parse("Fancy.brush", zip("Shape.png" to shape, "Brush.archive" to ByteArray(10)), decoder).brushes.single()
        assertEquals("Fancy", b.name); assertTrue(centerInk(b.tip) > 200); assertEquals(0, cornerInk(b.tip))
        val set = zip("A/Shape.png" to shape, "B/Shape.png" to shape, "brushset.plist" to ByteArray(4))
        assertEquals(2, BrushImporter.parse("s.brushset", set, decoder).brushes.size)
    }

    @Test fun hostileInputsAreRejected() {
        assertNotNull(BrushImporter.parse("a.txt", ByteArray(10), decoder).error)
        assertNotNull(BrushImporter.parse("a.abr", ByteArray(0), decoder).error)
        assertNotNull(BrushImporter.parse("a.wbrush", ByteArray(50) { 1 }, decoder).error)
        val bomb = zip("tip.png" to ByteArray(30 * 1024 * 1024))
        assertNull(Zips.read(bomb))
        assertNull(TipMasks.fromImage(RawImage(3000, 10, IntArray(30000) { 0xFF000000.toInt() }), InkMode.AUTO))
        assertNull(TipMasks.fromImage(RawImage(4, 4, IntArray(16) { 0xFFFFFFFF.toInt() }), InkMode.AUTO))
        val big = TipMasks.fromImage(RawImage(1024, 1024, IntArray(1024 * 1024) { 0xFF000000.toInt() }), InkMode.AUTO)!!
        assertEquals(512, big.width)
    }

    @Test fun storeRoundTripUpdateDeleteAndTraversalGuard() {
        val store = BrushStore(tmp.newFolder("brushes"))
        val parsed = GbrParser.parse(g)!!.copy(name = "Quote\" test", rotateWithStroke = true, angle = 45f)
        val saved = store.add(parsed)!!
        val listed = store.list().single()
        assertEquals("Quote\" test", listed.name); assertTrue(listed.rotateWithStroke)
        assertTrue(store.loadTip(listed)!!.alpha.contentEquals(parsed.tip.alpha))
        store.update(saved.copy(name = "Renamed", flow = 0.5f))
        assertEquals("Renamed", store.list().single().name)
        var threw = false
        try { store.delete("../x") } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        store.delete(saved.id)
        assertTrue(store.list().isEmpty())
    }
}

// ---- synthetic file builders ----
private val decoder: PngDecoder = { bytes -> decodePng(bytes) }

private fun createPng(w: Int, h: Int, argbPixels: IntArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()))

    val ihdrData = ByteArrayOutputStream()
    be32(ihdrData, w)
    be32(ihdrData, h)
    ihdrData.write(8)
    ihdrData.write(6)
    ihdrData.write(0)
    ihdrData.write(0)
    ihdrData.write(0)
    writeChunk(out, "IHDR", ihdrData.toByteArray())

    val rawScanlines = ByteArray(h * (1 + w * 4))
    for (y in 0 until h) {
        val rowOffset = y * (1 + w * 4)
        rawScanlines[rowOffset] = 0
        for (x in 0 until w) {
            val px = argbPixels[y * w + x]
            val a = (px ushr 24) and 0xFF
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val pxOffset = rowOffset + 1 + x * 4
            rawScanlines[pxOffset + 0] = r.toByte()
            rawScanlines[pxOffset + 1] = g.toByte()
            rawScanlines[pxOffset + 2] = b.toByte()
            rawScanlines[pxOffset + 3] = a.toByte()
        }
    }

    val deflater = Deflater()
    deflater.setInput(rawScanlines)
    deflater.finish()
    val buf = ByteArray(rawScanlines.size + 128)
    val compLen = deflater.deflate(buf)
    deflater.end()

    writeChunk(out, "IDAT", buf.copyOf(compLen))
    writeChunk(out, "IEND", ByteArray(0))
    return out.toByteArray()
}

private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
    be32(out, data.size)
    val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
    out.write(typeBytes)
    out.write(data)
    val crc = CRC32()
    crc.update(typeBytes)
    crc.update(data)
    be32(out, crc.value.toInt())
}

private fun decodePng(bytes: ByteArray): RawImage? {
    if (bytes.size < 8) return null
    if (bytes[0] != 0x89.toByte() || bytes[1] != 'P'.code.toByte() || bytes[2] != 'N'.code.toByte() || bytes[3] != 'G'.code.toByte()) return null

    var w = 0
    var h = 0
    var bitDepth = 0
    var colorType = 0
    val idatStream = ByteArrayOutputStream()
    var palette = IntArray(0)

    var p = 8
    val r = Be(bytes)
    while (p + 8 <= bytes.size) {
        val len = r.i32(p)
        val type = r.tag(p + 4)
        val dataPos = p + 8
        if (len < 0 || dataPos + len > bytes.size) break
        when (type) {
            "IHDR" -> {
                if (len < 13) return null
                w = r.i32(dataPos)
                h = r.i32(dataPos + 4)
                bitDepth = r.u8(dataPos + 8)
                colorType = r.u8(dataPos + 9)
            }
            "PLTE" -> {
                val count = len / 3
                palette = IntArray(count)
                for (i in 0 until count) {
                    val pr = r.u8(dataPos + i * 3)
                    val pg = r.u8(dataPos + i * 3 + 1)
                    val pb = r.u8(dataPos + i * 3 + 2)
                    palette[i] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
                }
            }
            "IDAT" -> {
                idatStream.write(bytes, dataPos, len)
            }
            "IEND" -> break
        }
        p = dataPos + len + 4
    }

    if (w <= 0 || h <= 0) return null

    val compressed = idatStream.toByteArray()
    if (compressed.isEmpty()) return null

    val bytesPerPixel = when (colorType) {
        0 -> 1
        2 -> 3
        3 -> 1
        4 -> 2
        6 -> 4
        else -> return null
    }

    if (bitDepth != 8) return null

    val rowBytes = w * bytesPerPixel
    val scanlinesSize = h * (1 + rowBytes)
    val decompressed = ByteArray(scanlinesSize)

    try {
        val inflater = Inflater()
        inflater.setInput(compressed)
        var count = 0
        while (!inflater.finished() && count < scanlinesSize) {
            val read = inflater.inflate(decompressed, count, scanlinesSize - count)
            if (read == 0) break
            count += read
        }
        inflater.end()
    } catch (e: Exception) {
        return null
    }

    val rawImagePx = IntArray(w * h)
    val uncompressedRows = ByteArray(h * rowBytes)

    for (y in 0 until h) {
        val filterType = decompressed[y * (1 + rowBytes)].toInt() and 0xFF
        val srcOffset = y * (1 + rowBytes) + 1
        val dstOffset = y * rowBytes

        for (x in 0 until rowBytes) {
            val rawByte = decompressed[srcOffset + x].toInt() and 0xFF
            val a = if (x >= bytesPerPixel) uncompressedRows[dstOffset + x - bytesPerPixel].toInt() and 0xFF else 0
            val b = if (y > 0) uncompressedRows[dstOffset - rowBytes + x].toInt() and 0xFF else 0
            val c = if (x >= bytesPerPixel && y > 0) uncompressedRows[dstOffset - rowBytes + x - bytesPerPixel].toInt() and 0xFF else 0

            val valUnfiltered = when (filterType) {
                0 -> rawByte
                1 -> (rawByte + a) and 0xFF
                2 -> (rawByte + b) and 0xFF
                3 -> (rawByte + (a + b) / 2) and 0xFF
                4 -> (rawByte + paethPredictor(a, b, c)) and 0xFF
                else -> rawByte
            }
            uncompressedRows[dstOffset + x] = valUnfiltered.toByte()
        }

        for (x in 0 until w) {
            val pxIdx = y * w + x
            val bIdx = dstOffset + x * bytesPerPixel
            val argb = when (colorType) {
                0 -> {
                    val g = uncompressedRows[bIdx].toInt() and 0xFF
                    (255 shl 24) or (g shl 16) or (g shl 8) or g
                }
                2 -> {
                    val red = uncompressedRows[bIdx].toInt() and 0xFF
                    val green = uncompressedRows[bIdx + 1].toInt() and 0xFF
                    val blue = uncompressedRows[bIdx + 2].toInt() and 0xFF
                    (255 shl 24) or (red shl 16) or (green shl 8) or blue
                }
                3 -> {
                    val idx = uncompressedRows[bIdx].toInt() and 0xFF
                    if (idx < palette.size) palette[idx] else 0xFF000000.toInt()
                }
                4 -> {
                    val g = uncompressedRows[bIdx].toInt() and 0xFF
                    val alpha = uncompressedRows[bIdx + 1].toInt() and 0xFF
                    (alpha shl 24) or (g shl 16) or (g shl 8) or g
                }
                6 -> {
                    val red = uncompressedRows[bIdx].toInt() and 0xFF
                    val green = uncompressedRows[bIdx + 1].toInt() and 0xFF
                    val blue = uncompressedRows[bIdx + 2].toInt() and 0xFF
                    val alpha = uncompressedRows[bIdx + 3].toInt() and 0xFF
                    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
                else -> 0
            }
            rawImagePx[pxIdx] = argb
        }
    }

    return RawImage(w, h, rawImagePx)
}

private fun paethPredictor(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = Math.abs(p - a)
    val pb = Math.abs(p - b)
    val pc = Math.abs(p - c)
    return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
}

private fun be32(o: ByteArrayOutputStream, v: Int) { o.write(v ushr 24); o.write(v ushr 16); o.write(v ushr 8); o.write(v) }
private fun be16(o: ByteArrayOutputStream, v: Int) { o.write(v ushr 8); o.write(v) }

private fun gbr(w: Int, h: Int, bytes: Int, name: String, spacing: Int, fill: (Int, Int) -> IntArray): ByteArray {
    val nm = name.toByteArray() + byteArrayOf(0)
    val o = ByteArrayOutputStream()
    be32(o, 28 + nm.size); be32(o, 2); be32(o, w); be32(o, h); be32(o, bytes)
    o.write("GIMP".toByteArray()); be32(o, spacing); o.write(nm)
    for (y in 0 until h) for (x in 0 until w) for (v in fill(x, y)) o.write(v)
    return o.toByteArray()
}

private fun packBits(row: ByteArray): ByteArray {
    val o = ByteArrayOutputStream()
    var i = 0
    while (i < row.size) {
        var run = 1
        while (i + run < row.size && row[i + run] == row[i] && run < 128) run++
        if (run >= 3) { o.write(257 - run); o.write(row[i].toInt()); i += run }
        else {
            val start = i; var n = 0
            while (i < row.size && n < 128) {
                var r = 1
                while (i + r < row.size && row[i + r] == row[i] && r < 3) r++
                if (r >= 3) break
                i++; n++
            }
            o.write(n - 1); o.write(row, start, n)
        }
    }
    return o.toByteArray()
}

private fun roundPng(w: Int, h: Int, withAlpha: Boolean, inkDark: Boolean): ByteArray {
    val px = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val d = Math.hypot(x - w / 2.0, y - h / 2.0)
        val inside = d < w / 2.0 - 1
        val argb = if (withAlpha) { if (inside) 0xFF000000.toInt() else 0x00000000 }
                   else { val c = if (inside == inkDark) 0 else 255; (255 shl 24) or (c shl 16) or (c shl 8) or c }
        px[y * w + x] = argb
    }
    return createPng(w, h, px)
}

private fun zip(vararg e: Pair<String, ByteArray>): ByteArray {
    val o = ByteArrayOutputStream()
    ZipOutputStream(o).use { z -> for ((n, b) in e) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    return o.toByteArray()
}

private fun pngWithText(png: ByteArray, key: String, text: String): ByteArray {
    // insert a tEXt chunk before IEND
    val iend = png.size - 12
    val data = key.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)
    val chunk = ByteArrayOutputStream()
    be32(chunk, data.size)
    val typeAndData = "tEXt".toByteArray() + data
    chunk.write(typeAndData)
    val crc = CRC32(); crc.update(typeAndData); be32(chunk, crc.value.toInt())
    return png.copyOfRange(0, iend) + chunk.toByteArray() + png.copyOfRange(iend, png.size)
}


