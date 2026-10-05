package org.wishyclip.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrameOrderingTest {

    private lateinit var db: WishyDatabase
    private lateinit var repository: ProjectRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WishyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = BitmapStore(context)
        repository = ProjectRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFrameOrderingAndReordering() = runBlocking {
        // Create project
        val projectId = repository.createProject("Test Project", 1280, 720, 12)

        // Initial frame created at position 0
        var frames = repository.frames(projectId)
        assertEquals(1, frames.size)
        assertEquals(0, frames[0].position)

        // Insert frame at position 1
        val template = LayerEntity(frameId = 0, position = 0, name = "Layer 1", visible = true, opacity = 1f)
        repository.insertFrame(projectId, 1, listOf(template))

        // Insert frame at position 2
        repository.insertFrame(projectId, 2, listOf(template))

        frames = repository.frames(projectId)
        assertEquals(3, frames.size)
        assertEquals(listOf(0, 1, 2), frames.map { it.position })

        // Move frame 0 to position 2
        repository.moveFrame(projectId, 0, 2)
        frames = repository.frames(projectId)
        assertEquals(3, frames.size)
        assertEquals(listOf(0, 1, 2), frames.map { it.position })

        // Duplicate frame at index 1
        repository.duplicateFrame(frames[1], 2)
        frames = repository.frames(projectId)
        assertEquals(4, frames.size)
        assertEquals(listOf(0, 1, 2, 3), frames.map { it.position })

        // Delete frame at index 0
        repository.deleteFrame(frames[0])
        frames = repository.frames(projectId)
        assertEquals(3, frames.size)
        assertEquals(listOf(0, 1, 2), frames.map { it.position })
    }
}
