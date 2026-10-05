package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream

/**
 * Disk storage for layer pixels. One PNG per layer: filesDir/projects/{projectId}/layers/{layerId}.png.
 * A missing file means a blank (transparent) layer. All methods are blocking: call from Dispatchers.IO.
 * TODO(webp): WEBP_LOSSLESS (API 30+) could replace PNG on newer devices; BitmapFactory sniffs the format.
 */
class BitmapStore(private val context: Context) {

    private fun projectDir(projectId: Long): File = File(context.filesDir, "projects/$projectId")

    fun layerFile(projectId: Long, layerId: Long): File =
        File(projectDir(projectId), "layers/$layerId.png")

    fun thumbFile(projectId: Long): File = File(projectDir(projectId), "thumb.png")

    /** Returns a mutable ARGB_8888 bitmap of exactly width x height. */
    fun load(projectId: Long, layerId: Long, width: Int, height: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = layerFile(projectId, layerId)
        if (file.exists()) {
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            if (decoded != null) {
                Canvas(result).drawBitmap(decoded, 0f, 0f, null)
                decoded.recycle()
            }
        }
        return result
    }

    fun save(projectId: Long, layerId: Long, bitmap: Bitmap) {
        writePng(layerFile(projectId, layerId), bitmap)
    }

    fun saveThumb(projectId: Long, bitmap: Bitmap) {
        writePng(thumbFile(projectId), bitmap)
    }

    fun deleteLayer(projectId: Long, layerId: Long) {
        layerFile(projectId, layerId).delete()
    }

    fun copyLayer(projectId: Long, fromLayerId: Long, toLayerId: Long) {
        val from = layerFile(projectId, fromLayerId)
        if (!from.exists()) return
        val to = layerFile(projectId, toLayerId)
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
    }

    fun deleteProject(projectId: Long) {
        projectDir(projectId).deleteRecursively()
    }

    private fun writePng(target: File, bitmap: Bitmap) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
