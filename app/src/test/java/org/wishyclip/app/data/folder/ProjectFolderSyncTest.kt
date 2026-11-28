package org.wishyclip.app.data.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wishyclip.app.data.BitmapStore
import org.wishyclip.app.data.ProjectRepository
import org.wishyclip.app.data.WishyDatabase

/** The whole loop on the JVM: edit in the app, mirror to the folder, "reinstall", import it back. */
@RunWith(RobolectricTestRunner::class)
class ProjectFolderSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var rootDir: File
    private var db: WishyDatabase? = null
    private lateinit var repo: ProjectRepository
    private lateinit var sync: ProjectFolderSync
    private var rootReady = true

    /** A fresh app install: empty database and empty app storage. The project folder is NOT touched. */
    private fun freshInstall() {
        db?.close()
        File(context.filesDir, "projects").deleteRecursively()
        File(context.filesDir, "folder-sync").deleteRecursively()
        val d = Room.inMemoryDatabaseBuilder(context, WishyDatabase::class.java).allowMainThreadQueries().build()
        db = d
        repo = ProjectRepository(d, BitmapStore(context))
        sync = ProjectFolderSync(repo) {
            if (rootReady) FolderAccess.RootState.Ready(FileFolderStore(rootDir)) else FolderAccess.RootState.Lost
        }
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        rootDir = tmp.newFolder("WishyClip")
        freshInstall()
    }

    @After fun teardown() { db?.close() }

    private fun solid(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    private suspend fun makeProject(name: String, color: Int): Long {
        val pid = repo.createProject(name, 16, 16, 12)
        val layer = repo.layers(repo.frames(pid).first().id).first()
        repo.store.save(pid, layer.id, solid(color))
        return pid
    }

    @Test fun savingWritesAFolderNamedAfterTheProject() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        val r = sync.sync(pid)
        assertTrue(r.toString(), r is SyncResult.Done)
        assertTrue(File(rootDir, "Cat/wishyclip-project.json").isFile)
        assertEquals("Cat", repo.getProject(pid)!!.folderName)
    }

    @Test fun twoProjectsWithTheSameNameGetDifferentFolders() = runBlocking {
        val a = makeProject("Cat", Color.RED)
        val b = makeProject("Cat", Color.BLUE)
        sync.sync(a); sync.sync(b)
        assertEquals(setOf("Cat", "Cat (2)"), rootDir.list()!!.toSet())
    }

    @Test fun syncingTwiceWithoutChangesDoesNotCreateNewRevisions() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        val first = sync.sync(pid) as SyncResult.Done
        val second = sync.sync(pid) as SyncResult.Done
        assertFalse(second.changed)
        assertEquals(first.revision, second.revision)
    }

    @Test fun projectSurvivesReinstallWithPixelsIntact() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)

        freshInstall() // uninstall + reinstall: app data is gone, the folder is not
        assertTrue(repo.allProjects().isEmpty())
        val found = sync.scan()!!
        assertEquals(1, found.size)
        assertEquals(FolderStatus.IMPORTABLE, found[0].status)

        val out = sync.importProject("Cat")
        val newId = (out as ImportOutcome.Imported).projectId
        val p = repo.getProject(newId)!!
        assertEquals("Cat", p.name)
        assertEquals(16, p.width)
        val layer = repo.layers(repo.frames(newId).first().id).first()
        assertEquals(Color.RED, repo.store.load(newId, layer.id, 16, 16).getPixel(3, 3))
        // Now it is known: scanning no longer offers it, and syncing does not duplicate the folder.
        assertTrue(sync.scan()!!.all { it.status == FolderStatus.ALREADY_IMPORTED })
        sync.sync(newId)
        assertEquals(listOf("Cat"), rootDir.list()!!.toList())
    }

    @Test fun importingTheSameProjectTwiceIsRefused() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        assertEquals(ImportOutcome.AlreadyHere, sync.importProject("Cat"))
        assertEquals(1, repo.allProjects().size)
    }

    @Test fun renamingTheProjectRenamesTheFolder() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        repo.updateProject(repo.getProject(pid)!!.copy(name = "Dog"))
        sync.sync(pid)
        assertEquals(listOf("Dog"), rootDir.list()!!.toList())
        assertEquals("Dog", repo.getProject(pid)!!.folderName)
    }

    @Test fun aFolderTheUserRenamedIsNotRenamedBack() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        assertTrue(File(rootDir, "Cat").renameTo(File(rootDir, "My favourite")))
        repo.setFolderName(pid, "My favourite")
        repo.updateProject(repo.getProject(pid)!!.copy(name = "Dog"))
        sync.sync(pid)
        assertEquals(listOf("My favourite"), rootDir.list()!!.toList())
    }

    @Test fun changeMadeInTheFolderIsReportedAndNotOverwritten() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        // Another device saves a newer revision into the same folder.
        val dir = FileFolderStore(File(rootDir, "Cat"))
        val m = (ManifestReader.readBest(dir) as FolderManifest.Found).manifest
        dir.writeFile(ProjectFormat.MANIFEST) { it.write(ManifestCodec.encode(m.copy(revision = 9, name = "Cat from phone")).toByteArray()) }
        // The app edits too.
        val layer = repo.layers(repo.frames(pid).first().id).first()
        repo.store.save(pid, layer.id, solid(Color.GREEN))
        val r = sync.sync(pid)
        assertTrue(r.toString(), r is SyncResult.Problem && r.kind == ProblemKind.CONFLICT)
        assertEquals("Cat from phone", (ManifestReader.readBest(dir) as FolderManifest.Found).manifest.name)
        // The user keeps the app's version: it is written as the next revision.
        val forced = sync.sync(pid, force = true)
        assertTrue(forced.toString(), forced is SyncResult.Done)
        assertEquals(10L, (ManifestReader.readBest(dir) as FolderManifest.Found).manifest.revision)
    }

    @Test fun noFolderChosenMeansNothingIsWrittenAndNothingIsLost() = runBlocking {
        rootReady = false
        val pid = makeProject("Cat", Color.RED)
        assertEquals(SyncResult.NoFolder, sync.sync(pid))
        assertNotNull(repo.getProject(pid))
        assertTrue(rootDir.list()!!.isEmpty())
    }

    @Test fun projectsCreatedBeforeTheFolderWasChosenAreExportedLater() = runBlocking {
        rootReady = false
        val a = makeProject("Old one", Color.RED)
        val b = makeProject("Old two", Color.BLUE)
        sync.syncAll()
        assertTrue(rootDir.list()!!.isEmpty())
        rootReady = true
        val results = sync.syncAll()
        assertTrue(results.all { it.second is SyncResult.Done })
        assertEquals(setOf("Old one", "Old two"), rootDir.list()!!.toSet())
        assertNotEquals(repo.getProject(a)!!.uuid, repo.getProject(b)!!.uuid)
    }

    @Test fun deletingFromTheAppKeepsTheFolderUnlessAsked() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        repo.deleteProject(pid)
        assertTrue(File(rootDir, "Cat").isDirectory)
        assertEquals(FolderStatus.IMPORTABLE, sync.scan()!![0].status)
        assertTrue(sync.deleteFolder("Cat"))
        assertFalse(File(rootDir, "Cat").exists())
    }

    @Test fun missingLayerFileBlocksImportUnlessARepairedCopyIsRequested() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        File(rootDir, "Cat/layers").listFiles()!!.forEach { it.delete() }
        freshInstall()
        val refused = sync.importProject("Cat")
        assertTrue(refused.toString(), refused is ImportOutcome.Incomplete)
        assertTrue(repo.allProjects().isEmpty())

        val repaired = sync.importProject("Cat", allowIncomplete = true) as ImportOutcome.Imported
        val copy = repo.getProject(repaired.projectId)!!
        assertNull(copy.folderName)
        // The original folder was not rewritten with the gap.
        val m = (ManifestReader.readBest(FileFolderStore(File(rootDir, "Cat"))) as FolderManifest.Found).manifest
        assertNotEquals(m.uuid, copy.uuid)
    }

    @Test fun aCorruptedLayerFileIsNeverInstalled() = runBlocking {
        val pid = makeProject("Cat", Color.RED)
        sync.sync(pid)
        val f = File(rootDir, "Cat/layers").listFiles()!!.first()
        f.writeBytes(ByteArray(f.length().toInt()) { 1 }) // same size, wrong content
        freshInstall()
        assertTrue(sync.importProject("Cat") is ImportOutcome.Incomplete)
        assertTrue(repo.allProjects().isEmpty())
    }

    @Test fun folderFromANewerVersionIsNotImportedOrTouched() = runBlocking {
        val dir = File(rootDir, "Future").also { it.mkdirs() }
        val text = """{"format":"wishyclip-project","formatVersion":7,"uuid":"abcdefgh-1"}"""
        File(dir, ProjectFormat.MANIFEST).writeText(text)
        assertEquals(FolderStatus.TOO_NEW, sync.scan()!![0].status)
        assertTrue(sync.importProject("Future") is ImportOutcome.TooNew)
        assertEquals(text, File(dir, ProjectFormat.MANIFEST).readText())
    }

    @Test fun unrelatedFoldersAreIgnored() = runBlocking {
        File(rootDir, "Holiday photos").mkdirs()
        File(rootDir, "Holiday photos/a.jpg").writeText("x")
        assertTrue(sync.scan()!!.isEmpty())
    }
}
