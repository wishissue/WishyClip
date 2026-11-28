package org.wishyclip.app.data.folder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wishyclip.app.data.AudioTrackEntity
import org.wishyclip.app.data.ProjectRepository
import org.wishyclip.app.data.ProjectSnapshot
import org.wishyclip.app.model.ProjectLimits
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class ProblemKind { CONFLICT, TOO_NEW, DAMAGED, FAILED }

sealed interface SyncResult {
    data class Done(val revision: Long, val changed: Boolean) : SyncResult
    /** No project folder chosen, or access to it is gone. Nothing is lost: the app keeps its own copy. */
    object NoFolder : SyncResult
    object Skipped : SyncResult
    data class Problem(val projectId: Long, val projectName: String, val kind: ProblemKind, val message: String) : SyncResult
}

enum class FolderStatus { IMPORTABLE, ALREADY_IMPORTED, CHANGED_OUTSIDE, TOO_NEW, DAMAGED }

class FolderEntry(
    val dirName: String,
    val status: FolderStatus,
    val projectName: String,
    val frames: Int,
    val updatedAt: Long,
    val localProjectId: Long?
)

sealed interface ImportOutcome {
    data class Imported(val projectId: Long) : ImportOutcome
    /** Some layer/audio files are missing or unreadable. Nothing was imported. */
    data class Incomplete(val missing: Int) : ImportOutcome
    object AlreadyHere : ImportOutcome
    object NoFolder : ImportOutcome
    data class TooLarge(val width: Int, val height: Int) : ImportOutcome
    data class TooNew(val version: Int) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}

/**
 * Keeps every project mirrored in the folder the user chose, and brings projects back from it.
 *
 * The app's own database/PNG copy is the fast working copy; the folder is the durable one. Every
 * operation takes one global lock, so a save and an import can never interleave. All blocking work runs
 * on Dispatchers.IO.
 */
class ProjectFolderSync(
    private val repo: ProjectRepository,
    /** Where the project folder is right now. Production reads the chosen folder; tests pass a plain directory. */
    private val rootProvider: suspend () -> FolderAccess.RootState
) {
    private val store get() = repo.store

    suspend fun rootState(): FolderAccess.RootState = try {
        rootProvider()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FolderAccess.RootState.NotConfigured
    }

    // ---------------------------------------------------------------- push (app -> folder)

    /** [force] = keep the app's version even if the folder was changed elsewhere (user decided). */
    suspend fun sync(projectId: Long, force: Boolean = false): SyncResult = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                syncLocked(projectId, force, allowRename = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncResult.Problem(projectId, "", ProblemKind.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun syncAll(): List<Pair<Long, SyncResult>> =
        repo.allProjects().map { it.id }.map { it to sync(it) }

    private suspend fun syncLocked(projectId: Long, force: Boolean, allowRename: Boolean): SyncResult {
        val root = (rootState() as? FolderAccess.RootState.Ready)?.store ?: return SyncResult.NoFolder
        val snap = repo.snapshot(projectId) ?: return SyncResult.Skipped
        val p = snap.project
        fun problem(kind: ProblemKind, msg: String) = SyncResult.Problem(projectId, p.name, kind, msg)
        if (snap.frames.isEmpty()) return problem(ProblemKind.FAILED, "Project has no frames")

        val resolved = resolveFolder(root, p.folderName, p.name, p.uuid) ?: return problem(
            ProblemKind.FAILED, "Could not create a folder for \"${p.name}\""
        )
        var folderName = resolved.first
        var dir = resolved.second
        if (folderName != p.folderName) repo.setFolderName(projectId, folderName)

        val state = readState(projectId)
        var known: Long? = state?.revision
        // A renamed project gets a renamed folder, but only if the folder still carries the old project's name:
        // a folder the user renamed by hand is never renamed back.
        val oldName = state?.name
        if (oldName != null && oldName != p.name && FolderNames.sanitize(oldName).let {
                folderName == it || Regex("^" + Regex.escape(it) + " \\(\\d+\\)$").matches(folderName)
            }) {
            val target = FolderNames.unique(FolderNames.sanitize(p.name), root.listDirs() - folderName)
            if (target != folderName && root.rename(folderName, target)) {
                root.dir(target, create = false)?.let { dir = it; folderName = target; repo.setFolderName(projectId, target) }
            }
        }
        if (force) (ManifestReader.readBest(dir) as? FolderManifest.Found)?.let { known = it.manifest.revision }

        val input = buildInput(snap)
        return when (val r = ProjectFolderWriter.write(dir, input, known)) {
            is WriteResult.Committed -> { writeState(projectId, r.revision, p.name); SyncResult.Done(r.revision, true) }
            is WriteResult.Unchanged -> { writeState(projectId, r.revision, p.name); SyncResult.Done(r.revision, false) }
            is WriteResult.Conflict -> problem(
                ProblemKind.CONFLICT, "\"${p.name}\" was changed in its folder by another copy of the app or another device."
            )
            is WriteResult.TooNew -> problem(
                ProblemKind.TOO_NEW, "\"${p.name}\" in the folder was saved by a newer WishyClip and was left untouched."
            )
            is WriteResult.Damaged -> problem(
                ProblemKind.DAMAGED, "The project file in \"$folderName\" is unreadable. It was left untouched."
            )
            is WriteResult.Foreign -> {
                // The folder name is taken by a different project: use another folder.
                if (!allowRename) problem(ProblemKind.FAILED, "Folder \"$folderName\" belongs to another project")
                else {
                    repo.setFolderName(projectId, null)
                    val taken = root.listDirs()
                    val fresh = FolderNames.unique(FolderNames.sanitize(p.name), taken + folderName)
                    repo.setFolderName(projectId, fresh)
                    syncLocked(projectId, false, allowRename = false)
                }
            }
            is WriteResult.Failed -> problem(ProblemKind.FAILED, r.message)
        }
    }

    /** The project's folder: the remembered one if it still exists, otherwise a new, uniquely named one. */
    private fun resolveFolder(root: FolderStore, remembered: String?, projectName: String, uuid: String): Pair<String, FolderStore>? {
        if (remembered != null) root.dir(remembered, create = false)?.let { return remembered to it }
        if (remembered == null) {
            // The folder may already hold this very project (same folder chosen again, or after a reinstall):
            // adopt it instead of creating "Name (2)" next to it.
            for (name in root.listDirs()) {
                val d = root.dir(name, create = false) ?: continue
                val found = try { ManifestReader.readBest(d) } catch (e: Exception) { null }
                if (found is FolderManifest.Found && found.manifest.uuid == uuid) return name to d
            }
        }
        val name = remembered ?: FolderNames.unique(FolderNames.sanitize(projectName), root.listDirs())
        val dir = root.dir(name, create = true) ?: return null
        return name to dir
    }

    private fun buildInput(snap: ProjectSnapshot): WriteInput {
        val p = snap.project
        val layerSources = HashMap<String, ByteSource>()
        val frames = snap.frames.map { (frame, layers) ->
            ManifestFrame(
                exposure = frame.exposureDuration,
                layers = layers.map { l ->
                    val file = store.layerFile(p.id, l.id)
                    val hashed = if (file.isFile && file.length() > 0) hashOf(file) else null
                    val name = hashed?.let { "$it.png" }
                    if (name != null && name !in layerSources) layerSources[name] = layerSource(file, hashed)
                    ManifestLayer(l.name, l.visible, l.opacity, l.locked, l.blendMode, name)
                }
            )
        }
        val audioSources = HashMap<String, ByteSource>()
        val usedNames = HashSet<String>()
        val audio = snap.audio.mapNotNull { t -> audioEntry(t, audioSources, usedNames) }
        val thumb = store.thumbFile(p.id).takeIf { it.isFile && it.length() > 0 }
            ?.let { f -> ByteSource(f.length()) { f.inputStream() } }
        return WriteInput(
            manifest = ProjectManifest(
                formatVersion = ProjectFormat.SUPPORTED_VERSION, uuid = p.uuid, name = p.name,
                width = p.width, height = p.height, fps = p.fps, createdAt = p.createdAt, updatedAt = p.updatedAt,
                revision = 0, savedAt = 0, writtenBy = "WishyClip", frames = frames, audio = audio
            ),
            layerSources = layerSources, audioSources = audioSources, thumbnail = thumb
        )
    }

    private fun audioEntry(t: AudioTrackEntity, sources: MutableMap<String, ByteSource>, used: MutableSet<String>): ManifestAudio? {
        val f = File(t.filePath)
        if (!f.isFile || f.length() <= 0) return null
        var name = f.name
        if (!ProjectFormat.OWN_AUDIO_FILE.matches(name)) {
            val ext = f.extension.filter { it.isLetterOrDigit() }.take(8).ifEmpty { "m4a" }
            name = "track_${t.id}.$ext"
        }
        if (!used.add(name)) return null
        val len = f.length()
        sources[name] = ByteSource(len) { f.inputStream() }
        return ManifestAudio(name, t.name, t.startFrame, t.trimStartMs, t.durationMs, t.volume)
    }

    private class Hashed(val length: Long, val modified: Long, val hash: String)

    private fun hashOf(file: File): String? {
        val len = file.length()
        val mod = file.lastModified()
        hashCache[file.absolutePath]?.let { if (it.length == len && it.modified == mod) return it.hash }
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            hashCache[file.absolutePath] = Hashed(len, mod, hash)
            hash
        } catch (e: IOException) {
            null
        }
    }

    /** Re-checks the bytes at upload time: a layer saved in between must not be stored under the old hash. */
    private fun layerSource(file: File, hash: String): ByteSource = ByteSource(file.length()) {
        val bytes = file.readBytes()
        if (sha256Hex(bytes) != hash) throw IOException("Layer changed while it was being saved; will retry")
        ByteArrayInputStream(bytes)
    }

    // ---------------------------------------------------------------- state sidecar

    private class SyncState(val revision: Long, val name: String?)

    private fun readState(projectId: Long): SyncState? = try {
        val f = store.syncStateFile(projectId)
        if (f.isFile) {
            val j = JSONObject(f.readText())
            val rev = j.optLong("revision", -1L)
            if (rev >= 0) SyncState(rev, if (j.has("name")) j.optString("name") else null) else null
        } else null
    } catch (e: Exception) {
        null
    }

    private fun writeState(projectId: Long, revision: Long, name: String? = null) {
        try {
            val f = store.syncStateFile(projectId)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            val j = JSONObject().put("revision", revision)
            if (name != null) j.put("name", name)
            tmp.writeText(j.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (_: Exception) {
            // The sidecar only improves conflict detection; losing it never loses project data.
        }
    }

    // ---------------------------------------------------------------- pull (folder -> app)

    /** What the project folder contains, compared with the projects the app already has. */
    suspend fun scan(): List<FolderEntry>? = lock.withLock {
        withContext(Dispatchers.IO) {
            val root = (rootState() as? FolderAccess.RootState.Ready)?.store ?: return@withContext null
            val local = repo.allProjects()
            val byUuid = local.associateBy { it.uuid }
            val out = ArrayList<FolderEntry>()
            for (name in root.listDirs()) {
                if (name.startsWith(".")) continue
                val dir = try { root.dir(name, create = false) } catch (e: Exception) { null } ?: continue
                when (val m = try { ManifestReader.readBest(dir) } catch (e: Exception) { FolderManifest.Damaged }) {
                    is FolderManifest.None -> Unit
                    is FolderManifest.TooNew -> out.add(FolderEntry(name, FolderStatus.TOO_NEW, name, 0, 0, null))
                    is FolderManifest.Damaged -> out.add(FolderEntry(name, FolderStatus.DAMAGED, name, 0, 0, null))
                    is FolderManifest.Found -> {
                        val mf = m.manifest
                        val mine = byUuid[mf.uuid]
                        val status = when {
                            mine == null -> FolderStatus.IMPORTABLE
                            (readState(mine.id)?.revision ?: Long.MAX_VALUE) < mf.revision -> FolderStatus.CHANGED_OUTSIDE
                            else -> FolderStatus.ALREADY_IMPORTED
                        }
                        out.add(FolderEntry(name, status, mf.name, mf.frames.size, mf.updatedAt, mine?.id))
                    }
                }
            }
            out
        }
    }

    /**
     * Copies a project from its folder into the app.
     *  - [replaceExisting]: the project is already in the app and the user chose the folder's version.
     *  - [allowIncomplete]: import even though some files are missing; the result is a separate copy
     *    (new id, not linked to the folder) so the original folder is never rewritten with the gaps.
     */
    suspend fun importProject(dirName: String, replaceExisting: Boolean = false, allowIncomplete: Boolean = false): ImportOutcome =
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    importLocked(dirName, replaceExisting, allowIncomplete)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ImportOutcome.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }

    private suspend fun importLocked(dirName: String, replaceExisting: Boolean, allowIncomplete: Boolean): ImportOutcome {
        val root = (rootState() as? FolderAccess.RootState.Ready)?.store ?: return ImportOutcome.NoFolder
        val dir = root.dir(dirName, create = false) ?: return ImportOutcome.Failed("Folder not found")
        val m = when (val r = ManifestReader.readBest(dir)) {
            is FolderManifest.Found -> r.manifest
            is FolderManifest.TooNew -> return ImportOutcome.TooNew(r.version)
            else -> return ImportOutcome.Failed("This folder does not contain a readable WishyClip project")
        }
        if (m.width > ProjectLimits.MAX_SIDE || m.height > ProjectLimits.MAX_SIDE ||
            m.width.toLong() * m.height > ProjectLimits.MAX_PIXELS
        ) return ImportOutcome.TooLarge(m.width, m.height)
        val existing = repo.projectByUuid(m.uuid)
        if (existing != null && !replaceExisting) return ImportOutcome.AlreadyHere

        // Cheap completeness check before anything is created.
        val layersDir = dir.dir(ProjectFormat.LAYERS_DIR, create = false)
        val layerSizes = layersDir?.listFiles() ?: emptyMap()
        val audioSizes = dir.dir(ProjectFormat.AUDIO_DIR, create = false)?.listFiles() ?: emptyMap()
        var missing = m.layerFiles().count { (layerSizes[it] ?: 0L) <= 0L } +
            m.audioFiles().count { (audioSizes[it] ?: 0L) <= 0L }
        if (missing > 0 && !allowIncomplete) return ImportOutcome.Incomplete(missing)

        val detached = missing > 0
        val (projectId, ids) = repo.createFromManifest(m, if (detached) "" else dirName, emptyMap())
        try {
            if (detached) {
                // A repaired copy gets its own identity and no folder until the next sync creates one.
                repo.setFolderName(projectId, null)
                repo.setUuid(projectId, java.util.UUID.randomUUID().toString())
            }
            val installed = HashMap<String, Boolean>()
            m.frames.forEachIndexed { fi, f ->
                f.layers.forEachIndexed { li, l ->
                    val name = l.file ?: return@forEachIndexed
                    if ((layerSizes[name] ?: 0L) <= 0L) return@forEachIndexed
                    val bytes = layersDir!!.openRead(name)?.use { it.readBytes() }
                    val ok = bytes != null && sha256Hex(bytes) + ".png" == name &&
                        store.installLayerFromStream(projectId, ids[fi][li], ByteArrayInputStream(bytes))
                    if (!ok) {
                        if (!allowIncomplete) throw IncompleteException()
                        missing++
                    }
                    installed[name] = ok
                }
            }
            val audioDir = store.audioDir(projectId).apply { mkdirs() }
            for (a in m.audio) {
                if ((audioSizes[a.file] ?: 0L) <= 0L) continue
                val target = File(audioDir, a.file)
                val tmp = File(audioDir, a.file + ".part")
                dir.dir(ProjectFormat.AUDIO_DIR, create = false)!!.openRead(a.file)!!.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                if (!tmp.renameTo(target)) throw IOException("Could not store ${a.file}")
                repo.addAudioTrack(
                    AudioTrackEntity(
                        projectId = projectId, filePath = target.absolutePath, name = a.name, startFrame = a.startFrame,
                        trimStartMs = a.trimStartMs, durationMs = a.durationMs, volume = a.volume
                    )
                )
            }
        } catch (e: IncompleteException) {
            withContext(NonCancellable) { repo.deleteProject(projectId) }
            return ImportOutcome.Incomplete(1)
        } catch (e: Throwable) {
            try { withContext(NonCancellable) { repo.deleteProject(projectId) } } catch (_: Exception) {}
            if (e is CancellationException) throw e
            return ImportOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (!detached) writeState(projectId, m.revision)
        if (existing != null) repo.deleteProject(existing.id)
        return ImportOutcome.Imported(projectId)
    }

    private class IncompleteException : Exception()

    // ---------------------------------------------------------------- delete

    /** Deletes the project's folder (only when the user explicitly asked for it). */
    suspend fun deleteFolder(folderName: String?): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (folderName.isNullOrEmpty()) return@withContext true
            val root = (rootState() as? FolderAccess.RootState.Ready)?.store ?: return@withContext false
            try { root.delete(folderName) } catch (e: Exception) { false }
        }
    }

    companion object {
        /** One lock for every folder operation in the process. */
        private val lock = Mutex()
        private val hashCache = ConcurrentHashMap<String, Hashed>()
    }
}

/**
 * Saves are requested after every successful editor save. Requests for one project are merged: the folder
 * is written once the project has been quiet for [debounceMs]. [problems] lists projects that could not
 * be written; an entry disappears when that project syncs successfully.
 */
class ProjectSyncScheduler(
    private val scope: CoroutineScope,
    private val sync: ProjectFolderSync,
    private val debounceMs: Long = 2_500L
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val _problems = MutableStateFlow<Map<Long, SyncResult.Problem>>(emptyMap())
    val problems: StateFlow<Map<Long, SyncResult.Problem>> = _problems

    fun request(projectId: Long) {
        jobs.remove(projectId)?.cancel()
        jobs[projectId] = scope.launch {
            delay(debounceMs)
            jobs.remove(projectId)
            run(projectId, force = false)
        }
    }

    /** Writes as soon as possible without making the caller wait (leaving the editor, app going to background). */
    fun requestNow(projectId: Long) {
        jobs.remove(projectId)?.cancel()
        scope.launch { run(projectId, force = false) }
    }

    /** Writes immediately and waits for the result. */
    suspend fun syncNow(projectId: Long) {
        jobs.remove(projectId)?.cancel()
        run(projectId, force = false)
    }

    suspend fun resolveKeepApp(projectId: Long) = run(projectId, force = true)

    fun syncEverything() {
        scope.launch {
            for ((id, r) in sync.syncAll()) record(r, id)
        }
    }

    private suspend fun run(projectId: Long, force: Boolean) = record(sync.sync(projectId, force), projectId)

    private fun record(r: SyncResult, id: Long? = null) {
        when (r) {
            is SyncResult.Problem -> _problems.value = _problems.value + (r.projectId to r)
            is SyncResult.Done -> if (id != null) _problems.value = _problems.value - id
            else -> Unit
        }
    }
}
