package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Disk storage for layer pixels. One PNG per layer: filesDir/projects/{projectId}/layers/{layerId}.png.
 * A missing file means a blank (transparent) layer. All methods are blocking: call from Dispatchers.IO.
 *
 * Safety rules (user pixels are irreplaceable):
 *  - Every write goes to a sibling ".tmp" file, is fsynced, and is then moved over the target atomically.
 *    A crash or full disk therefore leaves the previous valid file untouched.
 *  - A file that exists but cannot be decoded is never treated as "blank and safe to overwrite": it is
 *    renamed to "<name>.corrupt" so the bytes stay recoverable, and [lastLoadProblems] records it.
 *  - Leftover ".tmp" files from an interrupted write are swept by [sweepTempFiles].
 *
 * TODO(webp): WEBP_LOSSLESS (API 30+) could replace PNG on newer devices; BitmapFactory sniffs the format.
 */
class BitmapStore(private val context: Context) {

    /** Layer files that failed to decode since the app started (kept as ".corrupt" next to the original). */
    val lastLoadProblems: MutableList<File> = java.util.Collections.synchronizedList(mutableListOf())

    private fun projectDir(projectId: Long): File = File(context.filesDir, "projects/$projectId")

    fun layerFile(projectId: Long, layerId: Long): File =
        File(projectDir(projectId), "layers/$layerId.png")

    /** True when pixels were ever saved for this layer. A layer without a file is blank. */
    fun hasLayer(projectId: Long, layerId: Long): Boolean = layerFile(projectId, layerId).exists()

    fun thumbFile(projectId: Long): File = File(projectDir(projectId), "thumb.png")

    /** Returns a mutable ARGB_8888 bitmap of exactly width x height. */
    fun load(projectId: Long, layerId: Long, width: Int, height: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = layerFile(projectId, layerId)
        if (file.exists() && file.length() > 0) {
            if (!isLikelyValidImageFile(file)) {
                quarantine(file)
                return result
            }
            val decoded = try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: OutOfMemoryError) {
                result.recycle()
                throw e
            }

            if (decoded != null) {
                Canvas(result).drawBitmap(decoded, 0f, 0f, null)
                decoded.recycle()
            } else {
                quarantine(file)
            }
        }
        return result
    }

    /** The saved pixels as a bitmap, or null when the layer was never painted (blank). Caller recycles. */
    fun decodeOrNull(projectId: Long, layerId: Long): Bitmap? {
        val file = layerFile(projectId, layerId)
        if (!file.exists() || file.length() <= 0) return null
        if (!isLikelyValidImageFile(file)) {
            quarantine(file)
            return null
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        if (decoded == null) quarantine(file)
        return decoded
    }

    fun save(projectId: Long, layerId: Long, bitmap: Bitmap) {
        writePng(layerFile(projectId, layerId), bitmap)
    }

    /** Small preview of one frame (all visible layers), shown in the timeline. Missing = empty frame. */
    fun frameThumbFile(projectId: Long, frameId: Long): File =
        File(projectDir(projectId), "frame-thumbs/$frameId.png")

    fun saveFrameThumb(projectId: Long, frameId: Long, bitmap: Bitmap) {
        writePng(frameThumbFile(projectId, frameId), bitmap)
    }

    fun saveThumb(projectId: Long, bitmap: Bitmap) {
        writePng(thumbFile(projectId), bitmap)
    }

    fun deleteLayer(projectId: Long, layerId: Long) {
        layerFile(projectId, layerId).delete()
    }

    fun audioDir(projectId: Long): File = File(projectDir(projectId), "audio")

    /** Per-project record of what was last written to the project folder. */
    fun syncStateFile(projectId: Long): File = File(context.filesDir, "folder-sync/$projectId.json")

    /**
     * Installs a layer from a stream atomically (tmp, fsync, move). The bytes must decode as an image,
     * otherwise nothing is installed and false is returned.
     */
    fun installLayerFromStream(projectId: Long, layerId: Long, input: java.io.InputStream): Boolean {
        val target = layerFile(projectId, layerId)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                input.copyTo(out)
                out.flush()
                out.fd.sync()
            }
            if (!isLikelyValidImageFile(tmp)) {
                tmp.delete()
                return false
            }
            moveReplacing(tmp, target)
            return true
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /** Copies a layer file atomically. Throws on failure so callers never end up with a half-copied layer. */
    fun copyLayer(projectId: Long, fromLayerId: Long, toLayerId: Long) {
        val from = layerFile(projectId, fromLayerId)
        if (!from.exists()) return
        val to = layerFile(projectId, toLayerId)
        to.parentFile?.mkdirs()
        val tmp = File(to.parentFile, to.name + ".tmp")
        try {
            from.copyTo(tmp, overwrite = true)
            moveReplacing(tmp, to)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    fun deleteProject(projectId: Long) {
        projectDir(projectId).deleteRecursively()
        syncStateFile(projectId).delete()
    }

    /** Deletes every numeric project folder whose id is not in [keep]. Non-numeric folders are left alone. */
    fun deleteProjectsExcept(keep: Set<Long>) {
        File(context.filesDir, "projects").listFiles()?.forEach { dir ->
            val id = dir.name.toLongOrNull() ?: return@forEach
            if (dir.isDirectory && id !in keep) dir.deleteRecursively()
        }
    }

    /**
     * Removes ".tmp" files left behind by a write that was interrupted by a crash or kill.
     * They are never valid data (the real file is only replaced after a complete write).
     * Call once per project when it is opened. @return how many files were removed.
     */
    fun sweepTempFiles(projectId: Long): Int {
        var removed = 0
        val dir = projectDir(projectId)
        for (sub in listOf("layers", "frame-thumbs", "")) {
            val folder = if (sub.isEmpty()) dir else File(dir, sub)
            folder.listFiles { f -> f.isFile && f.name.endsWith(".tmp") }?.forEach {
                if (it.delete()) removed++
            }
        }
        return removed
    }

    /** Free bytes on the volume holding app data; used to refuse a save that cannot possibly fit. */
    fun usableSpaceBytes(): Long = context.filesDir.usableSpace

    private fun isLikelyValidImageFile(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 8) return false
                val b0 = header[0].toInt() and 0xFF
                val b1 = header[1].toInt() and 0xFF
                val b2 = header[2].toInt() and 0xFF
                val b3 = header[3].toInt() and 0xFF
                // PNG magic bytes: 0x89 'P' 'N' 'G'
                if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true
                // JPEG magic bytes: 0xFF 0xD8 0xFF
                if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true
                // WEBP magic bytes: 'R' 'I' 'F' 'F'
                if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    /** Keeps an undecodable file as "<name>.corrupt" instead of letting a later save overwrite it silently. */
    private fun quarantine(file: File) {
        val kept = File(file.parentFile, file.name + ".corrupt")
        try {
            if (kept.exists()) kept.delete()
            if (file.renameTo(kept)) lastLoadProblems.add(kept)
        } catch (_: Exception) {
            // Best effort: the load itself already degraded to a blank layer.
        }
    }

    private fun writePng(target: File, bitmap: Bitmap) {
        if (bitmap.isRecycled) throw java.lang.IllegalArgumentException("Cannot save a recycled bitmap")
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!ok) throw java.io.IOException("Failed to compress bitmap to PNG")
                out.flush()
                out.fd.sync()
            }
            if (tmp.length() <= 0L) throw java.io.IOException("Wrote an empty file for ${target.name}")
            moveReplacing(tmp, target)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /** Atomic replace when the filesystem supports it; otherwise a replacing move. Never a partial copy. */
    private fun moveReplacing(tmp: File, target: File) {
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
