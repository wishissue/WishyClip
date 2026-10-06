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
            File(dir, "tip.a8").writeBytes(brush.tip.alpha)
            val stored = StoredBrush(
                id, brush.name, brush.tip.width, brush.tip.height, brush.spacing, brush.angle,
                brush.rotateWithStroke, brush.scatter, brush.sizeJitter, brush.flow
            )
            File(dir, "brush.json").writeText(encode(stored))
            stored
        } catch (e: Exception) {
            dir.deleteRecursively()
            null
        }
    }

    fun update(brush: StoredBrush) {
        val dir = File(root, safe(brush.id))
        if (dir.isDirectory) File(dir, "brush.json").writeText(encode(brush))
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
        val t = json.readText()
        val w = MiniJson.number(t, "tipWidth")?.toInt() ?: return null
        val h = MiniJson.number(t, "tipHeight")?.toInt() ?: return null
        if (w <= 0 || h <= 0) return null
        return StoredBrush(
            id = dir.name,
            name = MiniJson.string(t, "name") ?: dir.name,
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
