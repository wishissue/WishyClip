package org.wishyclip.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishyclip.app.brush.BrushImporter
import org.wishyclip.app.brush.BrushLimits
import org.wishyclip.app.brush.BrushStore
import org.wishyclip.app.brush.PngDecoder
import org.wishyclip.app.brush.RawImage
import org.wishyclip.app.brush.StoredBrush
import org.wishyclip.app.canvas.DabBrush
import java.nio.ByteBuffer

class ImportOutcome(val added: List<StoredBrush>, val skipped: Int, val error: String?)

/** Imported tip brushes: persistence ([BrushStore]) plus cached ALPHA_8 tip bitmaps for drawing. */
class BrushLibrary(private val store: BrushStore) {

    private val tips = HashMap<String, Bitmap>()

    fun list(): List<StoredBrush> = store.list()

    suspend fun import(fileName: String, bytes: ByteArray): ImportOutcome = withContext(Dispatchers.Default) {
        val parsed = BrushImporter.parse(fileName, bytes, AndroidImageDecoder)
        if (parsed.error != null) return@withContext ImportOutcome(emptyList(), 0, parsed.error)
        val added = ArrayList<StoredBrush>()
        for (b in parsed.brushes) store.add(b)?.let { added.add(it) }
        if (added.isEmpty()) ImportOutcome(emptyList(), 0, "Could not save the imported brushes.")
        else ImportOutcome(added, parsed.skipped, null)
    }

    /** The ALPHA_8 tip of [brush], built once and cached. Null if its tip file is missing or corrupt. */
    fun tip(brush: StoredBrush): Bitmap? {
        tips[brush.id]?.let { if (!it.isRecycled) return it }
        val mask = store.loadTip(brush) ?: return null
        val b = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ALPHA_8)
        b.copyPixelsFromBuffer(ByteBuffer.wrap(mask.alpha))
        tips[brush.id] = b
        return b
    }

    /** Saves changed spacing/flow/scatter/... for an existing brush. */
    fun update(brush: StoredBrush) = store.update(brush)

    /** The stamp for [brush]. Null if its tip file is missing or corrupt. */
    fun dab(brush: StoredBrush): DabBrush? {
        val bmp = tip(brush) ?: return null
        return DabBrush(
            tip = bmp, spacing = brush.spacing, angle = brush.angle,
            rotateWithStroke = brush.rotateWithStroke, scatter = brush.scatter,
            sizeJitter = brush.sizeJitter, flow = brush.flow
        )
    }

    fun delete(id: String) {
        tips.remove(id)?.let { if (!it.isRecycled) it.recycle() }
        store.delete(id)
    }
}

/** Decodes images with BitmapFactory, refusing anything over the tip size limit before allocating. */
object AndroidImageDecoder : PngDecoder {
    override fun invoke(bytes: ByteArray): RawImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Oversized images are shrunk while decoding instead of being rejected: a 4000 px brush
        // PNG is perfectly reasonable, and tips are stored at 512 px anyway.
        var sample = 1
        while (bounds.outWidth / sample > BrushLimits.MAX_TIP_SIDE || bounds.outHeight / sample > BrushLimits.MAX_TIP_SIDE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded: Bitmap? = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: OutOfMemoryError) {
            null
        }
        val bmp = decoded ?: return null
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        return RawImage(w, h, px)
    }
}
