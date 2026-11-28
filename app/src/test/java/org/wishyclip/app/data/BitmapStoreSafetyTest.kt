package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression tests for the "never destroy the last valid version of a layer" rules. */
@RunWith(RobolectricTestRunner::class)
class BitmapStoreSafetyTest {
    private lateinit var context: Context
    private lateinit var store: BitmapStore

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = BitmapStore(context)
        File(context.filesDir, "projects").deleteRecursively()
    }

    private fun solid(color: Int): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test fun saveThenLoadRoundTripsPixels() {
        store.save(1, 10, solid(Color.RED))
        val loaded = store.load(1, 10, 8, 8)
        assertEquals(Color.RED, loaded.getPixel(3, 3))
    }

    @Test fun overwriteReplacesAndLeavesNoTempFile() {
        store.save(1, 10, solid(Color.RED))
        store.save(1, 10, solid(Color.BLUE))
        assertEquals(Color.BLUE, store.load(1, 10, 8, 8).getPixel(0, 0))
        val leftovers = store.layerFile(1, 10).parentFile!!.listFiles { f -> f.name.endsWith(".tmp") }
        assertEquals(0, leftovers!!.size)
    }

    @Test fun failedSaveKeepsThePreviousValidFile() {
        store.save(1, 10, solid(Color.GREEN))
        val recycled = solid(Color.RED).also { it.recycle() } // compress() on a recycled bitmap throws
        try { store.save(1, 10, recycled); throw AssertionError("expected failure") } catch (_: Exception) {}
        assertEquals(Color.GREEN, store.load(1, 10, 8, 8).getPixel(0, 0))
        assertEquals(0, store.layerFile(1, 10).parentFile!!.listFiles { f -> f.name.endsWith(".tmp") }!!.size)
    }

    @Test fun corruptLayerFileIsQuarantinedNotOverwrittenSilently() {
        val f = store.layerFile(1, 11)
        f.parentFile!!.mkdirs()
        f.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val loaded = store.load(1, 11, 8, 8) // degrades to blank instead of crashing
        assertNotNull(loaded)
        assertFalse(f.exists())
        assertTrue(File(f.parentFile, "11.png.corrupt").exists())
        assertEquals(1, store.lastLoadProblems.size)
    }

    @Test fun sweepRemovesOnlyTempFiles() {
        store.save(1, 12, solid(Color.RED))
        val dir = store.layerFile(1, 12).parentFile!!
        File(dir, "99.png.tmp").writeBytes(byteArrayOf(9))
        assertEquals(1, store.sweepTempFiles(1))
        assertTrue(store.hasLayer(1, 12))
    }

    @Test fun copyLayerCreatesIndependentIdenticalFile() {
        store.save(1, 20, solid(Color.MAGENTA))
        store.copyLayer(1, 20, 21)
        assertEquals(Color.MAGENTA, store.load(1, 21, 8, 8).getPixel(2, 2))
        store.deleteLayer(1, 20)
        assertTrue(store.hasLayer(1, 21))
    }

    @Test fun deleteProjectsExceptKeepsKnownAndIgnoresNonNumericFolders() {
        store.save(1, 1, solid(Color.RED))
        store.save(2, 1, solid(Color.RED))
        File(context.filesDir, "projects/notes").mkdirs()
        store.deleteProjectsExcept(setOf(1L))
        assertTrue(store.hasLayer(1, 1))
        assertFalse(store.hasLayer(2, 1))
        assertTrue(File(context.filesDir, "projects/notes").exists())
    }
}

@RunWith(RobolectricTestRunner::class)
class RepositorySafetyTest {
    private lateinit var db: WishyDatabase
    private lateinit var repo: ProjectRepository
    private lateinit var store: BitmapStore

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WishyDatabase::class.java).allowMainThreadQueries().build()
        store = BitmapStore(context)
        repo = ProjectRepository(db, store)
        File(context.filesDir, "projects").deleteRecursively()
    }

    @Test fun audioSplitIsAtomicAndKeepsBothHalves() = runBlocking {
        val pid = repo.createProject("p", 64, 64, 12)
        val original = repo.addAudioTrack(AudioTrackEntity(projectId = pid, filePath = "/x.m4a", name = "t", durationMs = 4000))
        repo.splitAudioTrack(
            original.copy(durationMs = 1500),
            original.copy(id = 0, startFrame = 18, trimStartMs = 1500, durationMs = 2500)
        )
        val tracks = repo.getAudioTracks(pid)
        assertEquals(2, tracks.size)
        assertEquals(4000L, tracks.sumOf { it.durationMs })
    }

    @Test fun duplicateFrameCopiesPixelsAndSurvivesReload() = runBlocking {
        val pid = repo.createProject("p", 8, 8, 12)
        val frame = repo.frames(pid).first()
        val layer = repo.layers(frame.id).first()
        store.save(pid, layer.id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.CYAN) })
        val (copy, copyLayers) = repo.duplicateFrame(frame, 1)
        assertEquals(2, repo.frames(pid).size)
        assertEquals(Color.CYAN, store.load(pid, copyLayers.first().id, 8, 8).getPixel(1, 1))
        assertEquals(1, copy.position)
    }

    @Test fun deleteFrameRemovesRowsFilesAndRenumbers() = runBlocking {
        val pid = repo.createProject("p", 8, 8, 12)
        val f0 = repo.frames(pid).first()
        val (f1, l1) = repo.insertFrame(pid, 1, repo.layers(f0.id))
        store.save(pid, l1.first().id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        repo.deleteFrame(f1)
        assertEquals(listOf(0), repo.frames(pid).map { it.position })
        assertFalse(store.hasLayer(pid, l1.first().id))
    }

    @Test fun deletingProjectRemovesEverything() = runBlocking {
        val pid = repo.createProject("p", 8, 8, 12)
        val layer = repo.layers(repo.frames(pid).first().id).first()
        store.save(pid, layer.id, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        repo.deleteProject(pid)
        assertEquals(null, repo.getProject(pid))
        assertFalse(store.hasLayer(pid, layer.id))
    }
}
