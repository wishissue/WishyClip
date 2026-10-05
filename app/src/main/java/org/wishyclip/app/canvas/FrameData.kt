package org.wishyclip.app.canvas

import android.graphics.Bitmap

/** In-memory layer: metadata + pixels. [version] vs [savedVersion] tracks unsaved changes. */
class LayerData(
    val id: Long,
    var name: String,
    var visible: Boolean,
    var opacity: Float,
    val bitmap: Bitmap
) {
    var version: Int = 0
    var savedVersion: Int = 0
    val dirty: Boolean get() = version != savedVersion
}

/** In-memory frame (only the current frame +-2 are kept by EditorViewModel). */
class FrameData(val frameId: Long, val layers: MutableList<LayerData>) {
    var metaVersion: Int = 0
    var metaSavedVersion: Int = 0
}

/** Immutable snapshot of a layer for the UI layer panel. */
data class LayerUi(val id: Long, val name: String, val visible: Boolean, val opacity: Float)
