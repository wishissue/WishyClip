package org.wishyclip.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectPersistenceTest {

    private lateinit var db: WishyDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var store: BitmapStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WishyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = BitmapStore(context)
        repository = ProjectRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testProjectCreationAndRetrieval() = runBlocking {
        val pid = repository.createProject("Animation", 1920, 1080, 24)
        val project = repository.getProject(pid)

        assertNotNull(project)
        assertEquals("Animation", project?.name)
        assertEquals(1920, project?.width)
        assertEquals(1080, project?.height)
        assertEquals(24, project?.fps)

        val frames = repository.frames(pid)
        assertEquals(1, frames.size)

        val layers = repository.layers(frames[0].id)
        assertEquals(1, layers.size)
        assertEquals("Layer 1", layers[0].name)
    }

    @Test
    fun testBitmapStoreSaveLoadAndLayerDelete() {
        val projectId = 100L
        val layerId = 500L
        val width = 100
        val height = 100

        // Create a bitmap with red color
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        source.setPixel(10, 10, Color.RED)

        // Save to store
        store.save(projectId, layerId, source)

        // Verify file exists
        val file = store.layerFile(projectId, layerId)
        assertTrue(file.exists())

        // Load back and verify pixel
        val loaded = store.load(projectId, layerId, width, height)
        assertEquals(Color.RED, loaded.getPixel(10, 10))

        // Delete layer
        store.deleteLayer(projectId, layerId)
        assertFalse(file.exists())

        source.recycle()
        loaded.recycle()
    }

    @Test
    fun testDeleteProjectRemovesFilesAndData() = runBlocking {
        val pid = repository.createProject("To Delete", 800, 600, 12)
        val frames = repository.frames(pid)
        val layers = repository.layers(frames[0].id)

        // Save dummy bitmap
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        store.save(pid, layers[0].id, bmp)
        bmp.recycle()

        assertTrue(store.layerFile(pid, layers[0].id).exists())

        // Delete project
        repository.deleteProject(pid)

        assertNull(repository.getProject(pid))
        assertFalse(store.layerFile(pid, layers[0].id).exists())
    }
}
