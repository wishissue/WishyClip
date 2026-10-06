package org.wishyclip.app.brush

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/** Safe zip reading: capped entry count, per-entry and total size (zip-bomb guard). */
object Zips {
    private const val MAX_ENTRIES = 4096
    private const val MAX_TOTAL = 96L * 1024 * 1024

    /** Returns entry name -> bytes, or null if the data is not a zip or exceeds the limits. */
    fun read(data: ByteArray): Map<String, ByteArray>? {
        if (data.size < 4 || data[0].toInt() != 0x50 || data[1].toInt() != 0x4B) return null
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(data)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (out.size >= MAX_ENTRIES) return null
                    if (entry.isDirectory) continue
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var size = 0L
                    while (true) {
                        val n = zin.read(chunk)
                        if (n < 0) break
                        size += n
                        total += n
                        if (size > BrushLimits.MAX_FILE_BYTES || total > MAX_TOTAL) return null
                        buf.write(chunk, 0, n)
                    }
                    out[entry.name.replace('\\', '/')] = buf.toByteArray()
                }
            }
        } catch (e: Exception) {
            return null
        }
        return out
    }
}

/** Minimal JSON helpers (the brush metadata is flat) so this package needs no org.json. */
object MiniJson {
    fun string(text: String, key: String): String? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(text) ?: return null
        return unescape(m.groupValues[1])
    }

    fun number(text: String, key: String): Float? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)").find(text)
            ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    fun bool(text: String, key: String): Boolean? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(true|false)").find(text) ?: return null
        return m.groupValues[1] == "true"
    }

    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                when (n) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) { sb.append(code.toChar()); i += 4 } else sb.append('u')
                        } else sb.append('u')
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

/** Wishy's own brush: a zip with brush.json (parameters) and tip.png. */
object WbrushParser {
    fun parse(entries: Map<String, ByteArray>, fallbackName: String, decode: PngDecoder): ParsedBrush? {
        val jsonBytes = entries.entries.firstOrNull { it.key.substringAfterLast('/').equals("brush.json", true) }?.value
        val tipBytes = entries.entries.firstOrNull { it.key.substringAfterLast('/').equals("tip.png", true) }?.value
            ?: return null
        val img = decode(tipBytes) ?: return null
        val mask = TipMasks.fromImage(img, InkMode.AUTO) ?: return null
        val json = if (jsonBytes != null) String(jsonBytes, Charsets.UTF_8) else ""
        return fromJson(json, mask, fallbackName)
    }

    fun fromJson(json: String, mask: TipMask, fallbackName: String): ParsedBrush {
        return ParsedBrush(
            name = (MiniJson.string(json, "name") ?: fallbackName).take(64).ifBlank { fallbackName },
            tip = mask,
            spacing = TipMasks.clampSpacing(MiniJson.number(json, "spacing") ?: 0.12f),
            angle = (MiniJson.number(json, "angle") ?: 0f).coerceIn(-360f, 360f),
            rotateWithStroke = MiniJson.bool(json, "rotateWithStroke") ?: false,
            scatter = (MiniJson.number(json, "scatter") ?: 0f).coerceIn(0f, 2f),
            sizeJitter = (MiniJson.number(json, "sizeJitter") ?: 0f).coerceIn(0f, 1f),
            flow = (MiniJson.number(json, "flow") ?: 1f).coerceIn(0.05f, 1f)
        )
    }
}

/** Reads text chunks from a PNG (Krita stores its preset XML in one). */
object PngText {
    private val SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isPng(d: ByteArray): Boolean {
        if (d.size < 8) return false
        for (i in 0 until 8) if (d[i] != SIG[i]) return false
        return true
    }

    fun chunks(d: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (!isPng(d)) return out
        val r = Be(d)
        var p = 8
        try {
            while (p + 12 <= d.size) {
                val len = r.i32(p)
                val type = r.tag(p + 4)
                val body = p + 8
                if (len < 0 || body.toLong() + len + 4 > d.size) break
                when (type) {
                    "tEXt" -> {
                        val z = indexOfZero(d, body, body + len)
                        if (z > body) out[String(d, body, z - body, Charsets.ISO_8859_1)] =
                            String(d, z + 1, body + len - z - 1, Charsets.UTF_8)
                    }
                    "zTXt" -> {
                        val z = indexOfZero(d, body, body + len)
                        if (z > body && z + 2 <= body + len) {
                            val text = inflate(d, z + 2, body + len)
                            if (text != null) out[String(d, body, z - body, Charsets.ISO_8859_1)] = String(text, Charsets.UTF_8)
                        }
                    }
                    "iTXt" -> {
                        val z = indexOfZero(d, body, body + len)
                        if (z > body && z + 3 <= body + len) {
                            val compressed = d[z + 1].toInt() != 0
                            val z2 = indexOfZero(d, z + 3, body + len)
                            val z3 = if (z2 >= 0) indexOfZero(d, z2 + 1, body + len) else -1
                            if (z3 >= 0) {
                                val textBytes = if (compressed) inflate(d, z3 + 1, body + len) else d.copyOfRange(z3 + 1, body + len)
                                if (textBytes != null) out[String(d, body, z - body, Charsets.ISO_8859_1)] = String(textBytes, Charsets.UTF_8)
                            }
                        }
                    }
                    "IEND" -> return out
                }
                p = body + len + 4
            }
        } catch (e: IndexOutOfBoundsException) {
            // return what we have
        }
        return out
    }

    private fun indexOfZero(d: ByteArray, from: Int, to: Int): Int {
        for (i in from until to) if (d[i].toInt() == 0) return i
        return -1
    }

    private fun inflate(d: ByteArray, from: Int, to: Int): ByteArray? {
        val inf = Inflater()
        return try {
            inf.setInput(d, from, to - from)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > 4 * 1024 * 1024) return null
            }
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            inf.end()
        }
    }
}

/** Krita .kpp presets and .bundle packs. Best effort: tip, name and spacing are recovered. */
object KritaParser {

    fun parseKpp(png: ByteArray, fallbackName: String, decode: PngDecoder): ParsedBrush? {
        val xml = unescape(PngText.chunks(png)["preset"] ?: return null)
        val name = Regex("<Preset[^>]*?\\bname=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() } ?: fallbackName
        val spacing = Regex("\\bspacing=\"([0-9.]+)\"").find(xml)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.12f
        val angle = Regex("\\bangle=\"(-?[0-9.]+)\"").find(xml)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val hardness = Regex("\\bhfade=\"([0-9.]+)\"").find(xml)?.groupValues?.get(1)?.toFloatOrNull()

        var mask: TipMask? = null
        for (m in Regex("<resource\\s+([^>]*)>([^<]*)</resource>").findAll(xml)) {
            if (!m.groupValues[1].contains("brushes")) continue
            val bytes = try {
                java.util.Base64.getMimeDecoder().decode(m.groupValues[2].trim())
            } catch (e: IllegalArgumentException) {
                continue
            }
            mask = tipFromBytes(bytes, decode)
            if (mask != null) break
        }
        if (mask == null) mask = TipMasks.round(hardness ?: 0.7f)
        return ParsedBrush(
            name = name.take(64), tip = mask, spacing = TipMasks.clampSpacing(spacing),
            angle = angle.coerceIn(-360f, 360f)
        )
    }

    /** A .bundle is a zip: presets in paintoppresets/, tips in brushes/. */
    fun parseBundle(entries: Map<String, ByteArray>, decode: PngDecoder): List<ParsedBrush> {
        val out = ArrayList<ParsedBrush>()
        for ((path, bytes) in entries) {
            if (out.size >= BrushLimits.MAX_BRUSHES) break
            if (path.endsWith(".kpp", true)) {
                val base = path.substringAfterLast('/').substringBeforeLast('.')
                parseKpp(bytes, base, decode)?.let { out.add(it) }
            }
        }
        if (out.isNotEmpty()) return out
        for ((path, bytes) in entries) {
            if (out.size >= BrushLimits.MAX_BRUSHES) break
            val base = path.substringAfterLast('/').substringBeforeLast('.')
            val lower = path.lc()
            when {
                lower.endsWith(".gbr") -> GbrParser.parse(bytes, base)?.let { out.add(it) }
                lower.endsWith(".abr") -> out.addAll(AbrParser.parse(bytes, base))
                lower.endsWith(".png") && lower.contains("brushes/") -> {
                    val img = decode(bytes)
                    val mask = if (img != null) TipMasks.fromImage(img, InkMode.AUTO) else null
                    if (mask != null) out.add(ParsedBrush(base, mask))
                }
            }
        }
        return out
    }

    private fun tipFromBytes(bytes: ByteArray, decode: PngDecoder): TipMask? {
        if (PngText.isPng(bytes)) {
            val img = decode(bytes) ?: return null
            return TipMasks.fromImage(img, InkMode.AUTO)
        }
        if (GbrParser.looksLikeGbr(bytes)) return GbrParser.parse(bytes)?.tip
        return null
    }

    private fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}

/** Procreate .brush / .brushset. Best effort: only the Shape.png tip is used (no grain/dynamics). */
object ProcreateParser {
    fun parse(entries: Map<String, ByteArray>, fallbackName: String, decode: PngDecoder): List<ParsedBrush> {
        val groups = LinkedHashMap<String, MutableMap<String, ByteArray>>()
        for ((path, bytes) in entries) {
            val dir = if (path.contains('/')) path.substringBeforeLast('/') else ""
            groups.getOrPut(dir) { LinkedHashMap() }[path.substringAfterLast('/')] = bytes
        }
        val out = ArrayList<ParsedBrush>()
        for ((dir, files) in groups) {
            if (out.size >= BrushLimits.MAX_BRUSHES) break
            val shape = files.entries.firstOrNull { it.key.equals("Shape.png", true) }?.value ?: continue
            val img = decode(shape) ?: continue
            val mask = TipMasks.fromImage(img, InkMode.LIGHT_IS_INK) ?: continue
            val name = if (dir.isEmpty()) fallbackName else dir.substringAfterLast('/')
            out.add(ParsedBrush(name.take(64).ifBlank { fallbackName }, mask, spacing = 0.1f))
        }
        return out
    }
}

class ImportResult(val brushes: List<ParsedBrush>, val skipped: Int, val error: String?)

/** Picks a parser from the file name / contents. */
object BrushImporter {

    fun parse(fileName: String, data: ByteArray, decode: PngDecoder): ImportResult {
        if (data.isEmpty()) return ImportResult(emptyList(), 0, "The file is empty.")
        if (data.size > BrushLimits.MAX_FILE_BYTES) return ImportResult(emptyList(), 0, "The file is larger than 20 MB.")
        val base = fileName.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Brush" }
        val ext = fileName.substringAfterLast('.', "").lc()
        val found: List<ParsedBrush> = try {
            when (ext) {
                "gbr" -> listOfNotNull(GbrParser.parse(data, base))
                "gih" -> listOfNotNull(GbrParser.parseGih(data, base))
                "abr" -> AbrParser.parse(data, base)
                "kpp" -> listOfNotNull(KritaParser.parseKpp(data, base, decode))
                "bundle" -> Zips.read(data)?.let { KritaParser.parseBundle(it, decode) } ?: emptyList()
                "wbrush" -> Zips.read(data)?.let { listOfNotNull(WbrushParser.parse(it, base, decode)) } ?: emptyList()
                "brush", "brushset" -> Zips.read(data)?.let { ProcreateParser.parse(it, base, decode) } ?: emptyList()
                "zip" -> Zips.read(data)?.let { z ->
                    val w = WbrushParser.parse(z, base, decode)
                    if (w != null) listOf(w) else ProcreateParser.parse(z, base, decode)
                } ?: emptyList()
                else -> return ImportResult(emptyList(), 0, "Unsupported file type: .$ext")
            }
        } catch (e: Exception) {
            return ImportResult(emptyList(), 0, "Could not read this brush file.")
        }
        if (found.isEmpty()) return ImportResult(emptyList(), 0, "No usable brush tips were found in this file.")
        val kept = found.take(BrushLimits.MAX_BRUSHES)
        return ImportResult(kept, found.size - kept.size, null)
    }

    val SUPPORTED_EXTENSIONS = listOf("wbrush", "bundle", "kpp", "abr", "gbr", "gih", "brush", "brushset", "zip")
}
