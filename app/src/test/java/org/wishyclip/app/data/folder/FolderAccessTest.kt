package org.wishyclip.app.data.folder

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderAccessTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun labelShowsTheFolderPathTheUserPicked() {
        assertEquals(
            "Documents/WishyClip",
            FolderAccess.label("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FWishyClip")
        )
        assertEquals("", FolderAccess.label(null))
        assertEquals("", FolderAccess.label("  "))
        assertEquals("Internal storage", FolderAccess.label("content://com.android.externalstorage.documents/tree/primary%3A"))
    }

    @Test fun fileStoreListsRenamesAndDeletesFolders() {
        val root = FileFolderStore(tmp.newFolder())
        assertNull(root.dir("Cat", create = false))
        val cat = root.dir("Cat", create = true)!!
        cat.writeFile("a.txt") { it.write(1) }
        assertEquals(listOf("Cat"), root.listDirs())
        assertTrue(root.rename("Cat", "Dog"))
        assertEquals(listOf("Dog"), root.listDirs())
        assertEquals(setOf("a.txt"), root.dir("Dog", false)!!.listFiles().keys)
        // Renaming onto an existing folder must not merge or overwrite it.
        root.dir("Bird", true)
        assertFalse(root.rename("Dog", "Bird"))
        assertEquals(setOf("Bird", "Dog"), root.listDirs().toSet())
        assertTrue(root.delete("Dog"))
        assertEquals(listOf("Bird"), root.listDirs())
    }

    @Test fun overwritingAFileReplacesItCompletely() {
        val d = FileFolderStore(tmp.newFolder())
        d.writeFile("x") { it.write(ByteArray(100) { 1 }) }
        d.writeFile("x") { it.write(ByteArray(10) { 2 }) }
        assertEquals(10L, d.listFiles()["x"])
        assertEquals(File(tmp.root, "none").exists(), false)
    }
}
