package org.wishyclip.app.data

/**
 * Minimal, bounds-checked reader for the `name` table of TrueType / OpenType (.ttf / .otf / .ttc)
 * fonts. Used so imported fonts are listed by their real family name ("Pacifico", "Roboto Bold")
 * instead of whatever the file happens to be called. Plain Kotlin, no Android classes, so it is
 * unit-testable off-device.
 */
object FontNames {

    class Names(val family: String?, val style: String?, val fullName: String?)

    private const val TAG_TRUETYPE = 0x00010000L
    private const val TAG_OTTO = 0x4F54544FL // "OTTO" (CFF outlines)
    private const val TAG_TRUE = 0x74727565L // "true" (old Apple TrueType)
    private const val TAG_TTCF = 0x74746366L // "ttcf" (collection)
    private const val TAG_NAME = 0x6E616D65L // "name"

    /** True if [bytes] starts with a font container signature Android can load. */
    fun isFontFile(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val tag = u32(bytes, 0)
        return tag == TAG_TRUETYPE || tag == TAG_OTTO || tag == TAG_TRUE || tag == TAG_TTCF
    }

    /** Preferred file extension for [bytes] (without the dot). */
    fun extensionFor(bytes: ByteArray): String {
        if (bytes.size < 4) return "ttf"
        return when (u32(bytes, 0)) {
            TAG_OTTO -> "otf"
            TAG_TTCF -> "ttc"
            else -> "ttf"
        }
    }

    fun read(bytes: ByteArray): Names? = try {
        parse(bytes)
    } catch (e: Exception) {
        null
    }

    /**
     * Human-readable name: the family, followed by the style unless it is just "Regular"
     * (so "Roboto" and "Roboto Bold", never "Roboto Regular"). Null if the font has no usable name.
     */
    fun displayName(bytes: ByteArray): String? {
        val n = read(bytes) ?: return null
        val family = clean(n.family)
        if (family.isEmpty()) return clean(n.fullName).ifEmpty { null }
        val style = clean(n.style)
        val plain = style.isEmpty() ||
            style.equals("Regular", ignoreCase = true) ||
            style.equals("Normal", ignoreCase = true) ||
            style.equals("Book", ignoreCase = true) ||
            family.endsWith(style, ignoreCase = true)
        return if (plain) family else "$family $style"
    }

    private fun clean(s: String?): String =
        (s ?: "").filter { it >= ' ' && it != '\u007F' }.trim().take(64)

    private fun parse(b: ByteArray): Names? {
        if (!isFontFile(b)) return null
        var base = 0
        if (u32(b, 0) == TAG_TTCF) {
            if (b.size < 16) return null
            base = u32(b, 12).toInt() // offset of the first font in the collection
            if (base < 0 || base + 12 > b.size) return null
        }
        val numTables = u16(b, base + 4)
        var nameOffset = -1
        for (i in 0 until numTables) {
            val rec = base + 12 + i * 16
            if (rec + 16 > b.size) return null
            if (u32(b, rec) == TAG_NAME) {
                nameOffset = u32(b, rec + 8).toInt()
                break
            }
        }
        if (nameOffset < 0 || nameOffset + 6 > b.size) return null

        val count = u16(b, nameOffset + 2)
        val stringsStart = nameOffset + u16(b, nameOffset + 4)

        fun find(nameId: Int): String? {
            var best: String? = null
            var bestScore = -1
            for (i in 0 until count) {
                val r = nameOffset + 6 + i * 12
                if (r + 12 > b.size) break
                if (u16(b, r + 6) != nameId) continue
                val platform = u16(b, r)
                val encoding = u16(b, r + 2)
                val language = u16(b, r + 4)
                val length = u16(b, r + 8)
                val offset = u16(b, r + 10)
                val start = stringsStart + offset
                if (length == 0 || start < 0 || start + length > b.size) continue
                val text = decode(b, start, length, platform, encoding) ?: continue
                if (text.isBlank()) continue
                val score = when {
                    platform == 3 && language == 0x0409 -> 4 // Windows, English (US)
                    platform == 3 -> 3
                    platform == 0 -> 2 // Unicode
                    platform == 1 && language == 0 -> 1 // Mac, English
                    else -> 0
                }
                if (score > bestScore) {
                    best = text
                    bestScore = score
                }
            }
            return best
        }

        // 16/17 are the "typographic" family/style and win over the legacy 1/2 pair.
        return Names(
            family = find(16) ?: find(1),
            style = find(17) ?: find(2),
            fullName = find(4)
        )
    }

    private fun decode(b: ByteArray, start: Int, length: Int, platform: Int, encoding: Int): String? =
        when {
            platform == 3 || platform == 0 -> String(b, start, length, Charsets.UTF_16BE)
            platform == 1 && encoding == 0 -> String(b, start, length, Charsets.ISO_8859_1)
            else -> null
        }

    private fun u16(b: ByteArray, o: Int): Int {
        if (o < 0 || o + 2 > b.size) throw IndexOutOfBoundsException()
        return ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    }

    private fun u32(b: ByteArray, o: Int): Long {
        if (o < 0 || o + 4 > b.size) throw IndexOutOfBoundsException()
        return ((b[o].toLong() and 0xFF) shl 24) or
            ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or
            (b[o + 3].toLong() and 0xFF)
    }
}
