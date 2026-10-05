package org.wishyclip.app.canvas

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UndoManagerTest {

    private fun createBitmap(): Bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

    @Test
    fun testPushAndPopUndoRedo() {
        val undoManager = UndoManager(5)
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        val bmp1 = createBitmap()
        val entry1 = UndoEntry(frameId = 1L, layerId = 10L, bitmap = bmp1)
        undoManager.push(entry1)

        assertTrue(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        val poppedUndo = undoManager.popUndo()
        assertEquals(entry1, poppedUndo)
        assertFalse(undoManager.canUndo)

        // Push to redo
        undoManager.pushRedo(poppedUndo!!)
        assertTrue(undoManager.canRedo)

        val poppedRedo = undoManager.popRedo()
        assertEquals(entry1, poppedRedo)
        assertFalse(undoManager.canRedo)
    }

    @Test
    fun testNewPushClearsRedo() {
        val undoManager = UndoManager(5)
        val bmp1 = createBitmap()
        val bmp2 = createBitmap()

        undoManager.push(UndoEntry(1L, 10L, bmp1))
        val entry = undoManager.popUndo()
        undoManager.pushRedo(entry!!)
        assertTrue(undoManager.canRedo)

        // Pushing new edit must clear redo history
        undoManager.push(UndoEntry(1L, 10L, bmp2))
        assertFalse(undoManager.canRedo)
        assertTrue(bmp1.isRecycled)
    }

    @Test
    fun testStackLimitEnforcement() {
        val limit = 3
        val undoManager = UndoManager(limit)
        val bitmaps = mutableListOf<Bitmap>()

        for (i in 1..5) {
            val bmp = createBitmap()
            bitmaps.add(bmp)
            undoManager.push(UndoEntry(1L, i.toLong(), bmp))
        }

        // Oldest 2 bitmaps should be recycled due to limit = 3
        assertTrue(bitmaps[0].isRecycled)
        assertTrue(bitmaps[1].isRecycled)
        assertFalse(bitmaps[2].isRecycled)
        assertFalse(bitmaps[3].isRecycled)
        assertFalse(bitmaps[4].isRecycled)

        // Pop undo entries in LIFO order
        assertEquals(bitmaps[4], undoManager.popUndo()?.bitmap)
        assertEquals(bitmaps[3], undoManager.popUndo()?.bitmap)
        assertEquals(bitmaps[2], undoManager.popUndo()?.bitmap)
        assertNull(undoManager.popUndo())
    }

    @Test
    fun testDropFrameAndLayer() {
        val undoManager = UndoManager(10)
        val bmpFrame1Layer1 = createBitmap()
        val bmpFrame1Layer2 = createBitmap()
        val bmpFrame2Layer1 = createBitmap()

        undoManager.push(UndoEntry(frameId = 1L, layerId = 100L, bitmap = bmpFrame1Layer1))
        undoManager.push(UndoEntry(frameId = 1L, layerId = 101L, bitmap = bmpFrame1Layer2))
        undoManager.push(UndoEntry(frameId = 2L, layerId = 200L, bitmap = bmpFrame2Layer1))

        // Drop entries for frameId = 1
        undoManager.dropFrame(1L)

        assertTrue(bmpFrame1Layer1.isRecycled)
        assertTrue(bmpFrame1Layer2.isRecycled)
        assertFalse(bmpFrame2Layer1.isRecycled)

        val remaining = undoManager.popUndo()
        assertNotNull(remaining)
        assertEquals(2L, remaining?.frameId)
        assertNull(undoManager.popUndo())
    }
}
