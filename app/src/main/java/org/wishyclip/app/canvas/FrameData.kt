package org.wishyclip.app.canvas

import android.graphics.Bitmap
import org.wishyclip.app.model.LayerBlend

/**
 * In-memory layer: metadata + pixels. [version] vs [savedVersion] tracks unsaved changes.
 *
 * Pixels are SPARSE: a layer that has never been painted holds no bitmap at all (a blank 1920x1080
 * layer would otherwise cost 7.9 MiB). The bitmap is created on first access to [bitmap] through
 * [allocator]. Code that only wants to READ or DRAW must check [allocated] / [peekBitmap] first,
 * so that looking at an empty layer never allocates.
 *
 * Invariant: an unallocated layer is fully transparent and has no layer file on disk.
 */
class LayerData(
    val id: Long,
    var name: String,
    var visible: Boolean,
    var opacity: Float,
    bitmap: Bitmap?,
    var locked: Boolean = false,
    var blendMode: LayerBlend = LayerBlend.NORMAL,
    val allocator: (() -> Bitmap)? = null
) {
    private var stored: Bitmap? = bitmap

    var version: Int = 0
    var savedVersion: Int = 0
    val dirty: Boolean get() = version != savedVersion

    /** The pixels, created on first use. Use for WRITING; to read/draw check [allocated] first. */
    val bitmap: Bitmap
        get() {
            stored?.let { if (!it.isRecycled) return it }
            val make = allocator
                ?: throw IllegalStateException("Layer $id has no pixels and no allocator")
            return make().also { stored = it }
        }

    /** True when this layer has pixels in memory (painted, loaded from disk, or imported). */
    val allocated: Boolean get() = stored?.isRecycled == false

    /** The pixels if they exist, otherwise null. Never allocates. */
    fun peekBitmap(): Bitmap? = stored?.takeIf { !it.isRecycled }

    /** Bytes of pixel memory currently held (0 for an empty layer). */
    val byteCount: Long get() = peekBitmap()?.byteCount?.toLong() ?: 0L

    /** Frees the pixels. The layer becomes empty in memory; save it first if it is dirty. */
    fun release() {
        stored?.let { if (!it.isRecycled) it.recycle() }
        stored = null
    }

    /** An independent copy (same ids and settings). Empty layers stay empty. */
    fun deepCopy(): LayerData =
        LayerData(id, name, visible, opacity, peekBitmap()?.snapshot(), locked, blendMode, allocator)
}

/** In-memory frame (only the current frame +-2 are kept by EditorViewModel, fewer under memory pressure). */
class FrameData(val frameId: Long, val layers: MutableList<LayerData>) {
    var metaVersion: Int = 0
    var metaSavedVersion: Int = 0

    /** Pixel bytes held by this frame's layers. */
    fun allocatedBytes(): Long = layers.sumOf { it.byteCount }

    /** Frees every layer's pixels. */
    fun release() {
        layers.forEach { it.release() }
    }
}

/** Immutable snapshot of a layer for the UI layer panel. */
data class LayerUi(
    val id: Long,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val locked: Boolean = false,
    val blendMode: LayerBlend = LayerBlend.NORMAL
)
