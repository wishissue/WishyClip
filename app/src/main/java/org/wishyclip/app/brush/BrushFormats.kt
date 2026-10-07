package org.wishyclip.app.brush

/*
 * Pure-Kotlin (no Android imports) brush tip parsers, so they can be unit-tested on the plain JVM.
 *
 * Every importer reduces a brush to a [TipMask] (an 8-bit alpha mask, 255 = full ink) plus a few
 * dab-engine parameters. Colour is applied at paint time, so colour tips become one-colour masks.
 */

/** Alpha mask for a brush tip: alpha[y * width + x], 0..255, 255 = full ink. */
class TipMask(val width: Int, val height: Int, val alpha: ByteArray)

/** ARGB pixels as decoded from an image file. */
class RawImage(val width: Int, val height: Int, val argb: IntArray)

/** Decodes PNG/JPEG bytes. Supplied by the platform (BitmapFactory on Android, ImageIO in tests). */
typealias PngDecoder = (ByteArray) -> RawImage?

data class ParsedBrush(
    val name: String,
    val tip: TipMask,
    /** Distance between dabs as a fraction of the dab diameter. */
    val spacing: Float = 0.12f,
    /** Fixed tip rotation in degrees. */
    val angle: Float = 0f,
    val rotateWithStroke: Boolean = false,
    /** Random offset of each dab as a fraction of the diameter. */
    val scatter: Float = 0f,
    /** Random size reduction per dab, 0..1. */
    val sizeJitter: Float = 0f,
    /** Opacity of a single dab, 0..1. */
    val flow: Float = 1f
)

object BrushLimits {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val MAX_BRUSHES = 512
    /** Larger source tips are rejected outright. */
    const val MAX_TIP_SIDE = 2048
    /** Stored tips are box-downsampled so their longest side is at most this. */
    const val STORE_TIP_SIDE = 512
    const val MIN_SPACING = 0.02f
    const val MAX_SPACING = 2.0f
}

enum class InkMode {
    /** Black is ink (GIMP/Krita convention). Alpha is multiplied in. */
    DARK_IS_INK,
    /** White is ink (Procreate shape convention). Alpha is multiplied in. */
    LIGHT_IS_INK,
    /** Use the alpha channel if the image has any transparency, otherwise treat black as ink. */
    AUTO
}

object TipMasks {

    fun fromImage(img: RawImage, mode: InkMode): TipMask? {
        val w = img.width
        val h = img.height
        if (w <= 0 || h <= 0 || w > BrushLimits.MAX_TIP_SIDE || h > BrushLimits.MAX_TIP_SIDE) return null
        val n = w * h
        if (img.argb.size < n) return null
        var hasAlpha = false
        for (i in 0 until n) {
            if ((img.argb[i] ushr 24) < 255) { hasAlpha = true; break }
        }
        val alphaOnly = mode == InkMode.AUTO && hasAlpha
        val out = ByteArray(n)
        var any = false
        for (i in 0 until n) {
            val p = img.argb[i]
            val a = p ushr 24
            val v: Int
            if (alphaOnly) {
                v = a
            } else {
                val lum = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
                v = if (mode == InkMode.LIGHT_IS_INK) lum * a / 255 else (255 - lum) * a / 255
            }
            if (v != 0) any = true
            out[i] = v.toByte()
        }
        if (!any) return null
        return limit(TipMask(w, h, out))
    }

    /** Box-downsamples so the longest side is at most [BrushLimits.STORE_TIP_SIDE]. */
    fun limit(mask: TipMask): TipMask {
        val longest = maxOf(mask.width, mask.height)
        if (longest <= BrushLimits.STORE_TIP_SIDE) return mask
        val scale = BrushLimits.STORE_TIP_SIDE.toFloat() / longest
        val nw = maxOf(1, Math.round(mask.width * scale))
        val nh = maxOf(1, Math.round(mask.height * scale))
        val out = ByteArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = y * mask.height / nh
            val y1 = maxOf(y0 + 1, (y + 1) * mask.height / nh)
            for (x in 0 until nw) {
                val x0 = x * mask.width / nw
                val x1 = maxOf(x0 + 1, (x + 1) * mask.width / nw)
                var sum = 0
                var count = 0
                for (yy in y0 until minOf(y1, mask.height)) {
                    for (xx in x0 until minOf(x1, mask.width)) {
                        sum += mask.alpha[yy * mask.width + xx].toInt() and 255
                        count++
                    }
                }
                out[y * nw + x] = (if (count == 0) 0 else sum / count).toByte()
            }
        }
        return TipMask(nw, nh, out)
    }

    /** A soft round dab, used when a preset has no embedded tip (Krita "auto brush"). */
    fun round(hardness: Float, side: Int = 128): TipMask {
        val hard = hardness.coerceIn(0f, 1f)
        val out = ByteArray(side * side)
        val c = (side - 1) / 2f
        val r = side / 2f
        for (y in 0 until side) {
            for (x in 0 until side) {
                val d = Math.hypot((x - c).toDouble(), (y - c).toDouble()).toFloat() / r
                val v = when {
                    d >= 1f -> 0f
                    d <= hard -> 1f
                    else -> 1f - (d - hard) / (1f - hard).coerceAtLeast(0.001f)
                }
                out[y * side + x] = Math.round(v * 255f).toByte()
            }
        }
        return TipMask(side, side, out)
    }

    fun clampSpacing(s: Float): Float =
        if (s.isNaN()) 0.12f else s.coerceIn(BrushLimits.MIN_SPACING, BrushLimits.MAX_SPACING)
}

/** Locale-independent lower-casing that compiles on every Kotlin version. */
internal fun String.lc(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(Character.toLowerCase(c))
    return sb.toString()
}

/** Big-endian reads with bounds checks (throws IndexOutOfBounds, callers catch). */
internal class Be(val d: ByteArray) {
    fun u8(p: Int): Int = d[p].toInt() and 255
    fun u16(p: Int): Int = (u8(p) shl 8) or u8(p + 1)
    fun i16(p: Int): Int = u16(p).toShort().toInt()
    fun i32(p: Int): Int = (u8(p) shl 24) or (u8(p + 1) shl 16) or (u8(p + 2) shl 8) or u8(p + 3)
    fun tag(p: Int): String = String(d, p, 4, Charsets.ISO_8859_1)
}

// ------------------------------------------------------------------------------------------ GIMP

object GbrParser {

    /** Parses one .gbr brush. Greyscale tips are treated as black = ink. */
    fun parse(data: ByteArray, fallbackName: String = "GIMP brush"): ParsedBrush? {
        return try {
            parseUnsafe(data, fallbackName)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    private fun parseUnsafe(data: ByteArray, fallbackName: String): ParsedBrush? {
        if (data.size < 28) return null
        val r = Be(data)
        val headerSize = r.i32(0)
        val version = r.i32(4)
        val w = r.i32(8)
        val h = r.i32(12)
        val bytes = r.i32(16)
        if (headerSize < 20 || headerSize > data.size) return null
        if (w <= 0 || h <= 0 || w > BrushLimits.MAX_TIP_SIDE || h > BrushLimits.MAX_TIP_SIDE) return null
        if (bytes < 1 || bytes > 4) return null

        var spacing = 0.25f
        var nameStart = 20
        if (version >= 2) {
            if (r.tag(20) != "GIMP") return null
            spacing = r.i32(24) / 100f
            nameStart = 28
        } else if (version != 1) {
            return null
        }
        var nameEnd = nameStart
        while (nameEnd < headerSize && data[nameEnd].toInt() != 0) nameEnd++
        val name = if (nameEnd > nameStart) String(data, nameStart, nameEnd - nameStart, Charsets.UTF_8) else fallbackName

        val total = w.toLong() * h.toLong() * bytes
        if (headerSize + total > data.size) return null
        val argb = IntArray(w * h)
        var p = headerSize
        for (i in 0 until w * h) {
            when (bytes) {
                1 -> { val g = r.u8(p); argb[i] = (255 shl 24) or (g shl 16) or (g shl 8) or g }
                2 -> { val g = r.u8(p); val a = r.u8(p + 1); argb[i] = (a shl 24) or (g shl 16) or (g shl 8) or g }
                3 -> argb[i] = (255 shl 24) or (r.u8(p) shl 16) or (r.u8(p + 1) shl 8) or r.u8(p + 2)
                else -> argb[i] = (r.u8(p + 3) shl 24) or (r.u8(p) shl 16) or (r.u8(p + 1) shl 8) or r.u8(p + 2)
            }
            p += bytes
        }
        val mask = TipMasks.fromImage(RawImage(w, h, argb), InkMode.AUTO) ?: return null
        return ParsedBrush(name, mask, spacing = TipMasks.clampSpacing(spacing))
    }

    /** A .gih pipe is a two-line text header followed by concatenated .gbr brushes; take the first. */
    fun parseGih(data: ByteArray, fallbackName: String = "GIMP brush pipe"): ParsedBrush? {
        var lines = 0
        var pos = 0
        var firstLineEnd = -1
        while (pos < data.size && lines < 2) {
            if (data[pos].toInt() == '\n'.toInt()) {
                lines++
                if (lines == 1) firstLineEnd = pos
            }
            pos++
        }
        if (lines < 2) return null
        val pipeName = if (firstLineEnd > 0) String(data, 0, firstLineEnd, Charsets.UTF_8).trim() else fallbackName
        val brush = parse(data.copyOfRange(pos, data.size), if (pipeName.isEmpty()) fallbackName else pipeName)
        return brush
    }

    /** True if [data] looks like a .gbr file (used to sniff embedded tips). */
    fun looksLikeGbr(data: ByteArray): Boolean {
        if (data.size < 28) return false
        val r = Be(data)
        return r.i32(4) in 1..3 && (r.i32(4) == 1 || r.tag(20) == "GIMP") && r.i32(0) in 20..data.size
    }
}

// ------------------------------------------------------------------------------------- Photoshop

object AbrParser {

    /** Parses every sampled (bitmap-tip) brush in an .abr file. Computed round brushes are skipped. */
    fun parse(data: ByteArray, baseName: String = "ABR brush"): List<ParsedBrush> {
        if (data.size < 8) return emptyList()
        return try {
            val r = Be(data)
            when (r.u16(0)) {
                1, 2 -> parseV12(r, data, baseName)
                6, 7, 10 -> parseV6(r, data, baseName)
                else -> emptyList()
            }
        } catch (e: IndexOutOfBoundsException) {
            emptyList()
        }
    }

    private fun parseV12(r: Be, data: ByteArray, baseName: String): List<ParsedBrush> {
        val version = r.u16(0)
        val count = r.u16(2)
        val out = ArrayList<ParsedBrush>()
        var pos = 4
        var index = 0
        while (index < count && index < BrushLimits.MAX_BRUSHES && pos + 6 <= data.size) {
            val type = r.u16(pos)
            val size = r.i32(pos + 2)
            val body = pos + 6
            val end = body.toLong() + size
            if (size < 0 || end > data.size) break
            if (type == 2) {
                var p = body + 4                       // miscellaneous
                val spacing = r.u16(p) / 100f; p += 2
                var name = "$baseName ${index + 1}"
                if (version == 2) {
                    val chars = r.i32(p); p += 4
                    if (chars in 1..256) {
                        name = String(data, p, (chars - 1) * 2, Charsets.UTF_16BE).ifEmpty { name }
                        p += chars * 2
                    }
                }
                p += 1                                  // antialiasing
                p += 8                                  // bounds (shorts)
                val top = r.i32(p); val left = r.i32(p + 4); val bottom = r.i32(p + 8); val right = r.i32(p + 12); p += 16
                val depth = r.u16(p); p += 2
                val compression = r.u8(p); p += 1
                val mask = readTip(r, data, p, end.toInt(), right - left, bottom - top, depth, compression)
                if (mask != null) out.add(ParsedBrush(name, mask, spacing = TipMasks.clampSpacing(spacing)))
            }
            pos = end.toInt()
            index++
        }
        return out
    }

    private fun parseV6(r: Be, data: ByteArray, baseName: String): List<ParsedBrush> {
        val out = ArrayList<ParsedBrush>()
        var pos = 4
        while (pos + 12 <= data.size && out.size < BrushLimits.MAX_BRUSHES) {
            if (r.tag(pos) != "8BIM") break
            val tag = r.tag(pos + 4)
            val len = r.i32(pos + 8)
            val blockStart = pos + 12
            val blockEnd = blockStart.toLong() + len
            if (len < 0 || blockEnd > data.size) break
            if (tag == "samp") {
                var q = blockStart
                while (q + 4 <= blockEnd && out.size < BrushLimits.MAX_BRUSHES) {
                    val recLen = r.i32(q)
                    val recStart = q + 4
                    val recEnd = recStart.toLong() + recLen
                    if (recLen <= 0 || recEnd > blockEnd) break
                    val mask = scanRecord(r, data, recStart, recEnd.toInt())
                    if (mask != null) out.add(ParsedBrush("$baseName ${out.size + 1}", mask, spacing = 0.15f))
                    q = ((recEnd.toInt() + 3) and 3.inv())
                }
            }
            pos = blockEnd.toInt()
        }
        return out
    }

    /**
     * v6+ sample records carry a variable descriptor before the image header, so rather than
     * trusting a fixed offset this finds the first plausible (rect, depth, compression) header.
     */
    private fun scanRecord(r: Be, data: ByteArray, start: Int, end: Int): TipMask? {
        val keyLen = r.u8(start)
        var p = start + 1 + keyLen
        while (p + 19 <= end) {
            val top = r.i32(p); val left = r.i32(p + 4); val bottom = r.i32(p + 8); val right = r.i32(p + 12)
            val depth = r.u16(p + 16)
            val comp = r.u8(p + 18)
            val w = right - left
            val h = bottom - top
            if (top in 0..65535 && left in 0..65535 && w in 1..BrushLimits.MAX_TIP_SIDE &&
                h in 1..BrushLimits.MAX_TIP_SIDE && (depth == 8 || depth == 16) && (comp == 0 || comp == 1)
            ) {
                val mask = readTip(r, data, p + 19, end, w, h, depth, comp)
                if (mask != null) return mask
            }
            p++
        }
        return null
    }

    private fun readTip(r: Be, data: ByteArray, start: Int, end: Int, w: Int, h: Int, depth: Int, comp: Int): TipMask? {
        if (w <= 0 || h <= 0 || w > BrushLimits.MAX_TIP_SIDE || h > BrushLimits.MAX_TIP_SIDE) return null
        if (depth != 8 && depth != 16) return null
        val bpp = depth / 8
        val rowBytes = w * bpp
        val rows = ByteArray(rowBytes * h)
        if (comp == 0) {
            if (start.toLong() + rows.size > end) return null
            System.arraycopy(data, start, rows, 0, rows.size)
        } else if (comp == 1) {
            if (start.toLong() + 2L * h > end) return null
            var src = start + 2 * h
            for (y in 0 until h) {
                val packed = r.u16(start + 2 * y)
                val rowEnd = src + packed
                if (rowEnd > end) return null
                if (!unpackBits(data, src, rowEnd, rows, y * rowBytes, rowBytes)) return null
                src = rowEnd
            }
        } else return null

        val out = ByteArray(w * h)
        var any = false
        for (i in 0 until w * h) {
            // Sampled tips are stored as an alpha mask: 255 = full ink. 16-bit keeps the high byte.
            val v = rows[i * bpp]
            if (v.toInt() != 0) any = true
            out[i] = v
        }
        if (!any) return null
        return TipMasks.limit(TipMask(w, h, out))
    }

    /** PackBits decode of [src] [from, to) into exactly [count] bytes of [dst] at [dstPos]. */
    internal fun unpackBits(src: ByteArray, from: Int, to: Int, dst: ByteArray, dstPos: Int, count: Int): Boolean {
        var s = from
        var d = 0
        while (d < count) {
            if (s >= to) return false
            val n = src[s++].toInt()
            if (n >= 0) {
                val len = n + 1
                if (s + len > to || d + len > count) return false
                System.arraycopy(src, s, dst, dstPos + d, len)
                s += len
                d += len
            } else if (n != -128) {
                val len = 1 - n
                if (s >= to || d + len > count) return false
                val b = src[s++]
                for (k in 0 until len) dst[dstPos + d + k] = b
                d += len
            }
        }
        return true
    }
}

// ------------------------------------------------------------------------------------ plain images

/**
 * A plain PNG/JPEG/WebP used as a brush tip. Transparent images use their alpha channel; opaque
 * ones are judged by their corners: a dark background means "light shape on dark" (Procreate
 * shape style), anything else means "dark shape on light" (GIMP/Krita style).
 */
object ImageTipParser {

    fun parse(data: ByteArray, name: String, decode: PngDecoder): ParsedBrush? {
        val img = decode(data) ?: return null
        val mask = TipMasks.fromImage(img, inkModeFor(img)) ?: return null
        return ParsedBrush(name, mask, spacing = 0.15f)
    }

    fun inkModeFor(img: RawImage): InkMode {
        val n = img.width * img.height
        if (n <= 0 || img.argb.size < n) return InkMode.AUTO
        for (i in 0 until n) {
            if ((img.argb[i] ushr 24) < 255) return InkMode.AUTO // has transparency: alpha is the shape
        }
        fun lum(x: Int, y: Int): Int {
            val p = img.argb[y * img.width + x]
            return (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
        }
        val r = img.width - 1
        val b = img.height - 1
        val corners = (lum(0, 0) + lum(r, 0) + lum(0, b) + lum(r, b)) / 4
        return if (corners < 128) InkMode.LIGHT_IS_INK else InkMode.DARK_IS_INK
    }
}
