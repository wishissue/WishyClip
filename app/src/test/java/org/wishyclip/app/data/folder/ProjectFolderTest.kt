package org.wishyclip.app.data.folder

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json needs Robolectric on the JVM. */
@RunWith(RobolectricTestRunner::class)
class ProjectFolderTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun src(bytes: ByteArray) = ByteSource(bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    private fun png(seed: Int) = ByteArray(40) { (it + seed).toByte() }
    private fun hashName(b: ByteArray) = sha256Hex(b) + ".png"

    private fun input(uuid: String = "uuid-1234-abcd", name: String = "Cat", layers: List<ByteArray?>, audio: Map<String, ByteArray> = emptyMap()): WriteInput {
        val sources = HashMap<String, ByteSource>()
        val ml = layers.mapIndexed { i, b ->
            val n = b?.let { hashName(it) }
            if (b != null) sources[n!!] = src(b)
            ManifestLayer("Layer $i", true, 1f, false, "NORMAL", n)
        }
        return WriteInput(
            ProjectManifest(1, uuid, name, 640, 480, 12, 1, 2, 0, 0, "", listOf(ManifestFrame(1, ml)),
                audio.map { ManifestAudio(it.key, it.key, 0, 0, 1000, 1f) }),
            sources, audio.mapValues { src(it.value) }, null
        )
    }

    private fun dir() = FileFolderStore(tmp.newFolder())

    @Test fun firstWriteCreatesManifestFilesAndReadme() {
        val d = dir()
        val r = ProjectFolderWriter.write(d, input(layers = listOf(png(1), null)), null)
        assertEquals(WriteResult.Committed(1), r)
        val found = ManifestReader.readBest(d) as FolderManifest.Found
        assertEquals("Cat", found.manifest.name)
        assertEquals(1L, found.manifest.revision)
        assertEquals(1, d.dir("layers", false)!!.listFiles().size)
        assertTrue(ProjectFormat.README in d.listFiles())
        assertFalse(ProjectFormat.MANIFEST_TMP in d.listFiles())
    }

    @Test fun unchangedContentWritesNothingNew() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        val r = ProjectFolderWriter.write(d, input(layers = listOf(png(1))), 1)
        assertEquals(WriteResult.Unchanged(1), r)
    }

    @Test fun changeBumpsRevisionKeepsBackupAndCollectsOldLayerOnlyAfterTwoRevisions() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        ProjectFolderWriter.write(d, input(layers = listOf(png(2))), 1)
        val layers = d.dir("layers", false)!!
        // revision 1's picture is still there: the .bak refers to it.
        assertTrue(hashName(png(1)) in layers.listFiles())
        assertEquals(1L, ManifestReader.readBackup(d)!!.revision)
        ProjectFolderWriter.write(d, input(layers = listOf(png(3))), 2)
        assertFalse(hashName(png(1)) in layers.listFiles())
        assertTrue(hashName(png(2)) in layers.listFiles())
        assertTrue(hashName(png(3)) in layers.listFiles())
    }

    @Test fun foreignFilesAreNeverDeleted() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        val layers = d.dir("layers", false)!!
        layers.writeFile("holiday.png") { it.write(1) }
        layers.writeFile("notes.txt") { it.write(1) }
        ProjectFolderWriter.write(d, input(layers = listOf(png(2))), 1)
        ProjectFolderWriter.write(d, input(layers = listOf(png(3))), 2)
        assertTrue("holiday.png" in layers.listFiles())
        assertTrue("notes.txt" in layers.listFiles())
    }

    @Test fun newerFormatIsNeverTouched() {
        val d = dir()
        d.writeFile(ProjectFormat.MANIFEST) {
            it.write("""{"format":"wishyclip-project","formatVersion":99,"uuid":"uuid-1234-abcd"}""".toByteArray())
        }
        val before = d.openRead(ProjectFormat.MANIFEST)!!.use { it.readBytes().toList() }
        val r = ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        assertEquals(WriteResult.TooNew(99), r)
        assertEquals(before, d.openRead(ProjectFormat.MANIFEST)!!.use { it.readBytes().toList() })
        assertEquals(setOf(ProjectFormat.MANIFEST), d.listFiles().keys)
    }

    @Test fun damagedManifestIsNeverOverwritten() {
        val d = dir()
        d.writeFile(ProjectFormat.MANIFEST) { it.write("{ not json".toByteArray()) }
        assertEquals(WriteResult.Damaged, ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null))
        assertEquals(setOf(ProjectFormat.MANIFEST), d.listFiles().keys)
    }

    @Test fun otherProjectsFolderIsRefused() {
        val d = dir()
        ProjectFolderWriter.write(d, input(uuid = "uuid-other-0001", layers = listOf(png(1))), null)
        val r = ProjectFolderWriter.write(d, input(layers = listOf(png(2))), null)
        assertTrue(r is WriteResult.Foreign)
    }

    @Test fun changeMadeElsewhereIsReportedAsConflictNotOverwritten() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        ProjectFolderWriter.write(d, input(layers = listOf(png(2))), 1) // "another device" -> revision 2
        val r = ProjectFolderWriter.write(d, input(layers = listOf(png(9))), 1) // we still believe revision 1
        assertEquals(WriteResult.Conflict(2), r)
        assertEquals(2L, (ManifestReader.readBest(d) as FolderManifest.Found).manifest.revision)
    }

    @Test fun killedBeforeFinalManifestStillReadsTheNewestCompleteOne() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        // Simulate a crash after .tmp was written but before .json was replaced.
        val newer = (ManifestReader.readBest(d) as FolderManifest.Found).manifest.copy(revision = 2, name = "Cat v2")
        d.writeFile(ProjectFormat.MANIFEST_TMP) { it.write(ManifestCodec.encode(newer).toByteArray()) }
        assertEquals("Cat v2", (ManifestReader.readBest(d) as FolderManifest.Found).manifest.name)
        // A half-written .tmp is ignored.
        d.writeFile(ProjectFormat.MANIFEST_TMP) { it.write("{\"format\":\"wishy".toByteArray()) }
        assertEquals("Cat", (ManifestReader.readBest(d) as FolderManifest.Found).manifest.name)
    }

    @Test fun failedUploadCommitsNothing() {
        val d = dir()
        ProjectFolderWriter.write(d, input(layers = listOf(png(1))), null)
        val bad = input(layers = listOf(png(2)))
        val broken = WriteInput(bad.manifest, mapOf(hashName(png(2)) to ByteSource(40) { throw IOException("disk full") }), emptyMap(), null)
        val r = ProjectFolderWriter.write(d, broken, 1)
        assertTrue(r is WriteResult.Failed)
        assertEquals(1L, (ManifestReader.readBest(d) as FolderManifest.Found).manifest.revision)
    }

    @Test fun storeThatCutsAFileShortIsDetected() {
        val real = dir()
        val cutting = object : FolderStore by real {
            override fun dir(name: String, create: Boolean): FolderStore? =
                if (name == "layers") real.dir(name, create)?.let { Truncating(it) } else real.dir(name, create)
        }
        val r = ProjectFolderWriter.write(cutting, input(layers = listOf(png(1))), null)
        assertTrue(r is WriteResult.Failed)
        assertEquals(FolderManifest.None, ManifestReader.readBest(real))
    }

    private class Truncating(private val inner: FolderStore) : FolderStore by inner {
        override fun writeFile(name: String, write: (OutputStream) -> Unit) =
            inner.writeFile(name) { out -> write(object : OutputStream() {
                var n = 0
                override fun write(b: Int) { if (n++ < 10) out.write(b) }
            }) }
    }

    @Test fun audioIsStoredAndOnlyOwnTracksAreCollected() {
        val d = dir()
        val a = "track_1_2.m4a"
        ProjectFolderWriter.write(d, input(layers = listOf(png(1)), audio = mapOf(a to ByteArray(30) { 7 })), null)
        assertTrue(a in d.dir("audio", false)!!.listFiles())
        d.dir("audio", false)!!.writeFile("my-song.mp3") { it.write(1) }
        ProjectFolderWriter.write(d, input(layers = listOf(png(2))), 1)
        ProjectFolderWriter.write(d, input(layers = listOf(png(3))), 2)
        val files = d.dir("audio", false)!!.listFiles().keys
        assertFalse(a in files)
        assertTrue("my-song.mp3" in files)
    }

    // ---- codec / names

    @Test fun codecRoundTripsAndRejectsUnsafeNames() {
        val m = (ManifestReader.readBest(dir().also { ProjectFolderWriter.write(it, input(layers = listOf(png(1), null)), null) }) as FolderManifest.Found).manifest
        val back = (ManifestCodec.decode(ManifestCodec.encode(m)) as DecodeResult.Ok).manifest
        assertEquals(m, back)
        val evil = ManifestCodec.encode(m).replace(hashName(png(1)), "../../x.png")
        assertTrue(ManifestCodec.decode(evil) is DecodeResult.Invalid)
    }

    @Test fun codecRejectsAbsurdSizesAndGarbage() {
        val m = (ManifestReader.readBest(dir().also { ProjectFolderWriter.write(it, input(layers = listOf(png(1))), null) }) as FolderManifest.Found).manifest
        val huge = ManifestCodec.encode(m).replace("\"width\": 640", "\"width\": 99999")
        assertTrue(ManifestCodec.decode(huge) is DecodeResult.Invalid)
        assertTrue(ManifestCodec.decode("") is DecodeResult.Invalid)
        assertTrue(ManifestCodec.decode("[1,2,3]") is DecodeResult.Invalid)
        assertTrue(ManifestCodec.decode("{\"format\":\"other\"}") is DecodeResult.Invalid)
    }

    @Test fun unknownFieldsAreIgnored() {
        val m = (ManifestReader.readBest(dir().also { ProjectFolderWriter.write(it, input(layers = listOf(png(1))), null) }) as FolderManifest.Found).manifest
        val extra = ManifestCodec.encode(m).replaceFirst("{", "{\"futureField\": {\"x\": 1},")
        assertEquals(m, (ManifestCodec.decode(extra) as DecodeResult.Ok).manifest)
    }

    @Test fun folderNamesAreSafeAndUnique() {
        assertEquals("a_b_c", FolderNames.sanitize("a/b:c"))
        assertEquals("Untitled", FolderNames.sanitize("..."))
        assertEquals("hidden", FolderNames.sanitize(".hidden"))
        assertEquals(FolderNames.MAX_LENGTH, FolderNames.sanitize("x".repeat(500)).length)
        assertEquals("Cat (2)", FolderNames.unique("Cat", listOf("cat")))
        assertEquals("Cat (3)", FolderNames.unique("Cat", listOf("Cat", "Cat (2)")))
        assertEquals("Dog", FolderNames.unique("Dog", listOf("Cat")))
    }

    @Test fun fileStoreRejectsPathTricks() {
        val d = dir()
        for (bad in listOf("../x", "a/b", "..", "")) {
            try { d.writeFile(bad) { it.write(1) }; throw AssertionError("accepted $bad") } catch (_: IOException) {}
        }
        assertNotNull(d)
    }
}
