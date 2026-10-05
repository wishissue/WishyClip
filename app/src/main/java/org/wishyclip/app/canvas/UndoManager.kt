package org.wishyclip.app.canvas

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Stores pixels of one layer from before (undo) or after (redo) an edit.
 * If [rect] is null, [bitmap] is the whole layer; otherwise it is a small patch that belongs
 * at [rect] (far cheaper to take and keep than a full-canvas copy).
 */
class UndoEntry(val frameId: Long, val layerId: Long, val bitmap: Bitmap, val rect: Rect? = null)

/**
 * Snapshot-based undo/redo with limit enforcement and bitmap resource management.
 * Entries of frames that leave the +-2 memory cache are dropped via [dropFrame].
 */
class UndoManager(private val limit: Int, private val maxBytes: Long = Long.MAX_VALUE) {
    private val undo = ArrayDeque<UndoEntry>()
    private val redo = ArrayDeque<UndoEntry>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** A new user edit: clears redo history. */
    fun push(entry: UndoEntry) {
        redo.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        redo.clear()
        undo.addLast(entry)
        while (undo.size > limit || (undo.size > 1 && undoBytes() > maxBytes)) {
            val removed = undo.removeFirst()
            if (!removed.bitmap.isRecycled) {
                removed.bitmap.recycle()
            }
        }
    }

    private fun undoBytes(): Long =
        undo.sumOf { if (it.bitmap.isRecycled) 0L else it.bitmap.byteCount.toLong() }

    fun popUndo(): UndoEntry? = undo.removeLastOrNull()
    fun popRedo(): UndoEntry? = redo.removeLastOrNull()

    fun pushRedo(entry: UndoEntry) {
        redo.addLast(entry)
    }

    /** Used when redoing: goes back on the undo stack without clearing redo. */
    fun pushUndoKeepRedo(entry: UndoEntry) {
        undo.addLast(entry)
    }

    fun dropFrame(frameId: Long) = drop { it.frameId == frameId }

    fun dropLayer(layerId: Long) = drop { it.layerId == layerId }

    fun clear() = drop { true }

    private fun drop(predicate: (UndoEntry) -> Boolean) {
        for (queue in listOf(undo, redo)) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (predicate(entry)) {
                    if (!entry.bitmap.isRecycled) {
                        entry.bitmap.recycle()
                    }
                    iterator.remove()
                }
            }
        }
    }
}
