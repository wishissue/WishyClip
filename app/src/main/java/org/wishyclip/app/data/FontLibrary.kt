package org.wishyclip.app.data

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import org.wishyclip.app.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A font the text tool can use. [key] is stable (file name for imported fonts), [label] is what people see. */
data class FontOption(val key: String, val label: String, val builtIn: Boolean)

class FontImportResult(val option: FontOption?, val error: String?, val alreadyImported: Boolean = false)

/**
 * Built-in text fonts plus fonts the user imported (.ttf / .otf / .ttc). Imported fonts live in
 * `filesDir/fonts/<file>` with a `<file>.label` sidecar holding the real family name read from the
 * font itself, so the picker shows "Pacifico" rather than "custom_1759.ttf".
 */
class FontLibrary(private val context: Context) {

    private val dir = File(context.filesDir, "fonts")
    private val typefaces = ConcurrentHashMap<String, Typeface>()

    /** Built-ins followed by imported fonts, sorted by name. Reads files: call off the main thread. */
    fun list(): List<FontOption> {
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS } ?: emptyArray()
        val imported = files.map { f -> FontOption(f.name, labelFor(f), false) }
            .sortedBy { it.label.lowercase() }
        return BUILT_IN + imported
    }

    fun import(originalName: String?, bytes: ByteArray): FontImportResult {
        if (!FontNames.isFontFile(bytes)) {
            return FontImportResult(null, "That file isn't a TrueType/OpenType font (.ttf / .otf).")
        }
        val label = FontNames.displayName(bytes) ?: prettifyFileName(originalName) ?: "Custom font"

        // Importing the same font twice should not pile up duplicates.
        val existing = dir.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.firstOrNull { it.length() == bytes.size.toLong() && labelFor(it) == label }
        if (existing != null) {
            return FontImportResult(FontOption(existing.name, label, false), null, alreadyImported = true)
        }

        return try {
            dir.mkdirs()
            val ext = FontNames.extensionFor(bytes)
            val stem = sanitizeStem(originalName) ?: "font"
            var file = File(dir, "$stem.$ext")
            var n = 2
            while (file.exists()) file = File(dir, "${stem}_${n++}.$ext")
            file.writeBytes(bytes)

            // Android returns a fallback font or throws for unusable data; make sure it really loads.
            val ok = try {
                Typeface.createFromFile(file)
                true
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                file.delete()
                return FontImportResult(null, "Android could not load that font file.")
            }
            File(dir, file.name + ".label").writeText(label)
            FontImportResult(FontOption(file.name, label, false), null)
        } catch (e: Exception) {
            FontImportResult(null, "Could not save the font.")
        }
    }

    fun delete(key: String) {
        val f = fileFor(key) ?: return
        f.delete()
        File(dir, f.name + ".label").delete()
        typefaces.remove(key)
    }

    /** The Typeface for [key]; null means "use the default font". */
    fun typeface(key: String): Typeface? {
        typefaces[key]?.let { return it }
        val tf: Typeface? = when (key) {
            "Inter" -> resourceFont(R.font.inter_regular)
            "Inter Bold" -> resourceFont(R.font.inter_bold)
            "Sans Serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            "Casual" -> Typeface.create("casual", Typeface.NORMAL)
            "Condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> {
                val f = fileFor(key)
                if (f != null) {
                    try {
                        Typeface.createFromFile(f)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
        }
        if (tf != null) typefaces[key] = tf
        return tf
    }

    private fun resourceFont(id: Int): Typeface? = try {
        ResourcesCompat.getFont(context, id)
    } catch (e: Exception) {
        null
    }

    /** Resolves [key] to a file inside the fonts folder, refusing anything that could escape it. */
    private fun fileFor(key: String): File? {
        if (key.isEmpty() || key.contains('/') || key.contains('\\') || key.startsWith(".")) return null
        val f = File(dir, key)
        return if (f.isFile) f else null
    }

    private fun labelFor(file: File): String {
        val sidecar = File(file.parentFile, file.name + ".label")
        try {
            if (sidecar.isFile) {
                val saved = sidecar.readText().trim()
                if (saved.isNotEmpty()) return saved
            }
        } catch (_: Exception) {
        }
        // Fonts imported by older versions (custom_<time>.ttf) have no label yet: read it from the font.
        val fromFont: String? = try {
            FontNames.displayName(file.readBytes())
        } catch (e: Exception) {
            null
        }
        val label = fromFont ?: prettifyFileName(file.name) ?: "Imported font"
        try {
            sidecar.writeText(label)
        } catch (_: Exception) {
        }
        return label
    }

    companion object {
        const val MAX_FONT_BYTES = 25 * 1024 * 1024
        const val DEFAULT_KEY = "Inter"
        private val EXTENSIONS = setOf("ttf", "otf", "ttc")

        val BUILT_IN = listOf(
            FontOption("Inter", "Inter", true),
            FontOption("Inter Bold", "Inter Bold", true),
            FontOption("Sans Serif", "Sans Serif", true),
            FontOption("Serif", "Serif", true),
            FontOption("Monospace", "Monospace", true),
            FontOption("Cursive", "Cursive", true),
            FontOption("Casual", "Casual", true),
            FontOption("Condensed", "Condensed", true)
        )

        /** "My_Font-Bold.ttf" -> "My Font Bold". Null if nothing readable is left. */
        fun prettifyFileName(name: String?): String? {
            if (name.isNullOrBlank()) return null
            val stem = name.substringBeforeLast('.', name)
            val pretty = stem.replace('_', ' ').replace('-', ' ').trim().take(64)
            return pretty.ifEmpty { null }
        }

        /** File-name-safe version of [name] without extension, or null if nothing usable remains. */
        fun sanitizeStem(name: String?): String? {
            if (name.isNullOrBlank()) return null
            val stem = name.substringBeforeLast('.', name)
            val safe = stem.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(48)
            return safe.ifEmpty { null }
        }
    }
}
