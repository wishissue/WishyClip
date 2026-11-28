package org.wishyclip.app.brush

import java.io.File

/** A brush as saved in the library (tip pixels are loaded separately via [BrushStore.loadTip]). */
data class StoredBrush(
    val id: String,
    val name: String,
    val tipWidth: Int,
    val tipHeight: Int,
    val spacing: Float,
    val angle: Float,
    val rotateWithStroke: Boolean,
    val scatter: Float,
    val sizeJitter: Float,
    val flow: Float
)

/**
 * Imported brushes live in `<root>/<id>/brush.json` + `tip.a8` (raw width*height alpha bytes).
 * Plain java.io so it is testable off-device.
 */
class BrushStore(private val root: File) {

    fun list(): List<StoredBrush> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.sortedBy { it.name }.mapNotNull { read(it) }
    }

    fun add(brush: ParsedBrush): StoredBrush? {
        val id = nextId()
        val dir = File(root, id)
        if (!dir.mkdirs()) return null
        return try {
            writeAtomically(File(dir, "tip.a8")) { it.write(brush.tip.alpha) }
            val stored = StoredBrush(
                id, brush.name, brush.tip.width, brush.tip.height, brush.spacing, brush.angle,
                brush.rotateWithStroke, brush.scatter, brush.sizeJitter, brush.flow
            )
            writeAtomically(File(dir, "brush.json")) { it.write(encode(stored).toByteArray(Charsets.UTF_8)) }
            stored
        } catch (e: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    fun update(brush: StoredBrush) {
        val dir = File(root, safe(brush.id))
        if (dir.isDirectory) {
            writeAtomically(File(dir, "brush.json")) { it.write(encode(brush).toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Writes to "<name>.tmp", fsyncs, then renames over the target: a crash never leaves a truncated file. */
    private fun writeAtomically(target: File, write: (java.io.FileOutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            java.io.FileOutputStream(tmp).use { out ->
                write(out)
                out.flush()
                out.fd.sync()
            }
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    fun delete(id: String) {
        File(root, safe(id)).deleteRecursively()
    }

    fun loadTip(brush: StoredBrush): TipMask? {
        val f = File(File(root, safe(brush.id)), "tip.a8")
        if (!f.isFile) return null
        val bytes = f.readBytes()
        if (bytes.size != brush.tipWidth * brush.tipHeight) return null
        return TipMask(brush.tipWidth, brush.tipHeight, bytes)
    }

    private fun read(dir: File): StoredBrush? {
        val json = File(dir, "brush.json")
        if (!json.isFile) return null
        val t = try { json.readText() } catch (e: java.io.IOException) { return null }
        if (t.length > 4096) return null // a real brush.json is a few hundred bytes
        val w = MiniJson.number(t, "tipWidth")?.toInt() ?: return null
        val h = MiniJson.number(t, "tipHeight")?.toInt() ?: return null
        if (w <= 0 || h <= 0) return null
        return StoredBrush(
            id = dir.name,
            name = (MiniJson.string(t, "name") ?: dir.name).take(64),
            tipWidth = w, tipHeight = h,
            spacing = TipMasks.clampSpacing(MiniJson.number(t, "spacing") ?: 0.12f),
            angle = MiniJson.number(t, "angle") ?: 0f,
            rotateWithStroke = MiniJson.bool(t, "rotateWithStroke") ?: false,
            scatter = (MiniJson.number(t, "scatter") ?: 0f).coerceIn(0f, 2f),
            sizeJitter = (MiniJson.number(t, "sizeJitter") ?: 0f).coerceIn(0f, 1f),
            flow = (MiniJson.number(t, "flow") ?: 1f).coerceIn(0.05f, 1f)
        )
    }

    private fun encode(b: StoredBrush): String =
        "{\"name\":" + MiniJson.quote(b.name) +
            ",\"tipWidth\":" + b.tipWidth + ",\"tipHeight\":" + b.tipHeight +
            ",\"spacing\":" + b.spacing + ",\"angle\":" + b.angle +
            ",\"rotateWithStroke\":" + b.rotateWithStroke + ",\"scatter\":" + b.scatter +
            ",\"sizeJitter\":" + b.sizeJitter + ",\"flow\":" + b.flow + "}"

    private fun nextId(): String {
        root.mkdirs()
        var n = System.currentTimeMillis()
        while (File(root, "b$n").exists()) n++
        return "b$n"
    }

    /** Ids come from directory names we created; refuse anything that could escape [root]. */
    private fun safe(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() }) { "bad brush id" }
        return id
    }
}
