package org.wishyclip.app.data.folder

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** A file to upload: its exact size and a way to open it. Opened more than once, so it must be repeatable. */
class ByteSource(val size: Long, val open: () -> InputStream?)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Everything the writer needs for one save. The manifest's revision and savedAt are set by the writer. */
class WriteInput(
    val manifest: ProjectManifest,
    /** "<sha256>.png" -> bytes, for every layer file the manifest refers to. */
    val layerSources: Map<String, ByteSource>,
    /** audio file name -> bytes, for every audio file the manifest refers to. */
    val audioSources: Map<String, ByteSource>,
    val thumbnail: ByteSource?
)

sealed interface WriteResult {
    data class Committed(val revision: Long) : WriteResult
    /** The folder already holds exactly this content. */
    data class Unchanged(val revision: Long) : WriteResult
    /** The folder was changed by something else since the last sync; nothing was written. */
    data class Conflict(val remoteRevision: Long) : WriteResult
    /** The folder was written by a newer WishyClip; nothing was written. */
    data class TooNew(val version: Int) : WriteResult
    /** A manifest file exists but none can be read; nothing was written so the bytes stay recoverable. */
    object Damaged : WriteResult
    /** The folder holds a different project (different uuid); nothing was written. */
    data class Foreign(val uuid: String) : WriteResult
    data class Failed(val message: String) : WriteResult
}

/**
 * Writes one project into its folder. The order is what makes it crash-safe:
 *  1. upload new immutable layer/audio files (never touching existing ones),
 *  2. write the complete new manifest as .tmp and read it back,
 *  3. keep the previous manifest as .bak,
 *  4. write the new manifest as .json and read it back,
 *  5. remove .tmp, then delete files no longer referenced by the new or the previous manifest.
 * Killing the app at any point leaves at least one complete, valid manifest plus all files it refers to.
 */
object ProjectFolderWriter {

    fun write(
        dir: FolderStore,
        input: WriteInput,
        /** Revision this app last wrote or read for this project, or null when it never synced it. */
        knownRevision: Long?,
        now: Long = System.currentTimeMillis(),
        writtenBy: String = "WishyClip"
    ): WriteResult {
        return try {
            writeUnsafe(dir, input, knownRevision, now, writtenBy)
        } catch (e: IOException) {
            WriteResult.Failed(e.message ?: "I/O error")
        } catch (e: SecurityException) {
            WriteResult.Failed("No permission: ${e.message}")
        } catch (e: RuntimeException) {
            WriteResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun writeUnsafe(
        dir: FolderStore, input: WriteInput, knownRevision: Long?, now: Long, writtenBy: String
    ): WriteResult {
        val local = input.manifest
        val remote: ProjectManifest? = when (val r = ManifestReader.readBest(dir)) {
            is FolderManifest.None -> null
            is FolderManifest.Found -> r.manifest
            is FolderManifest.TooNew -> return WriteResult.TooNew(r.version)
            is FolderManifest.Damaged -> return WriteResult.Damaged
        }
        if (remote != null) {
            if (remote.uuid != local.uuid) return WriteResult.Foreign(remote.uuid)
            if (knownRevision != null && remote.revision > knownRevision && !remote.sameContentAs(local)) {
                return WriteResult.Conflict(remote.revision)
            }
        }

        // 1. Files first. Existing files with the right size are trusted (names are content hashes).
        syncFiles(dir, ProjectFormat.LAYERS_DIR, local.layerFiles(), input.layerSources)
        syncFiles(dir, ProjectFormat.AUDIO_DIR, local.audioFiles(), input.audioSources)

        if (remote != null && remote.sameContentAs(local)) return WriteResult.Unchanged(remote.revision)

        val revision = maxOf(remote?.revision ?: 0L, knownRevision ?: 0L) + 1
        val manifest = local.copy(
            formatVersion = ProjectFormat.SUPPORTED_VERSION, revision = revision, savedAt = now, writtenBy = writtenBy
        )
        val text = ManifestCodec.encode(manifest)

        // 2. Complete new manifest as .tmp, verified.
        dir.writeFile(ProjectFormat.MANIFEST_TMP) { it.write(text.toByteArray(Charsets.UTF_8)) }
        verify(dir, ProjectFormat.MANIFEST_TMP, revision)

        // 3. Previous manifest becomes .bak (only if it is itself valid, so a good .bak is never replaced by junk).
        val files = dir.listFiles()
        if (ProjectFormat.MANIFEST in files) {
            val oldText = dir.openRead(ProjectFormat.MANIFEST)?.use { it.readBytes() }
            if (oldText != null && ManifestCodec.decode(oldText.toString(Charsets.UTF_8)) is DecodeResult.Ok) {
                dir.writeFile(ProjectFormat.MANIFEST_BAK) { it.write(oldText) }
            }
        }

        // 4. The new manifest.
        dir.writeFile(ProjectFormat.MANIFEST) { it.write(text.toByteArray(Charsets.UTF_8)) }
        verify(dir, ProjectFormat.MANIFEST, revision)

        // 5. Cleanup and extras. None of this can lose data, so failures are ignored.
        runCatching { dir.delete(ProjectFormat.MANIFEST_TMP) }
        runCatching { collectGarbage(dir, manifest) }
        runCatching {
            input.thumbnail?.let { t ->
                dir.writeFile(ProjectFormat.THUMBNAIL) { out -> t.open()?.use { it.copyTo(out) } }
            }
        }
        runCatching {
            if (ProjectFormat.README !in dir.listFiles()) {
                dir.writeFile(ProjectFormat.README) { it.write(README_TEXT.toByteArray(Charsets.UTF_8)) }
            }
        }
        return WriteResult.Committed(revision)
    }

    private fun syncFiles(dir: FolderStore, sub: String, needed: Set<String>, sources: Map<String, ByteSource>) {
        if (needed.isEmpty()) return
        val target = dir.dir(sub, create = true) ?: throw IOException("Could not create $sub folder")
        val existing = target.listFiles()
        for (name in needed) {
            val src = sources[name] ?: throw IOException("No source for $name")
            if (existing[name] == src.size) continue
            target.writeFile(name) { out ->
                val input = src.open() ?: throw IOException("Could not read $name")
                input.use { it.copyTo(out) }
            }
            if (target.listFiles()[name] != src.size) throw IOException("$name was not written completely")
        }
    }

    private fun verify(dir: FolderStore, name: String, revision: Long) {
        val bytes = dir.openRead(name)?.use { it.readBytes() } ?: throw IOException("$name vanished after writing")
        val r = ManifestCodec.decode(bytes.toString(Charsets.UTF_8))
        if (r !is DecodeResult.Ok || r.manifest.revision != revision) throw IOException("$name did not verify")
    }

    private fun collectGarbage(dir: FolderStore, current: ProjectManifest) {
        val previous = ManifestReader.readBackup(dir)
        val keepLayers = current.layerFiles() + (previous?.layerFiles() ?: emptySet())
        val keepAudio = current.audioFiles() + (previous?.audioFiles() ?: emptySet())
        dir.dir(ProjectFormat.LAYERS_DIR, create = false)?.let { d ->
            d.listFiles().keys.filter { ProjectFormat.LAYER_FILE.matches(it) && it !in keepLayers }
                .forEach { d.delete(it) }
        }
        dir.dir(ProjectFormat.AUDIO_DIR, create = false)?.let { d ->
            d.listFiles().keys.filter { ProjectFormat.OWN_AUDIO_FILE.matches(it) && it !in keepAudio }
                .forEach { d.delete(it) }
        }
    }

    const val README_TEXT = """This is a WishyClip project folder.

wishyclip-project.json   the project (frames, layers, settings)
wishyclip-project.bak    the previous save, kept as a safety copy
layers/                  one PNG per layer picture (named by content, never edited)
audio/                   imported sound files
thumbnail.png            preview

You can copy, zip, share or back up this whole folder. Open WishyClip, choose the folder that contains it
as your project folder, and the project appears. Please do not edit the .json by hand while the app is
running. Files in layers/ are plain PNG images and can be opened in any image editor, but changes made
there are not read back by the app.
"""
}
