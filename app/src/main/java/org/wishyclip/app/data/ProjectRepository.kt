package org.wishyclip.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A consistent read of one project, frames bottom-to-top layers in order. */
class ProjectSnapshot(
    val project: ProjectEntity,
    val frames: List<Pair<FrameEntity, List<LayerEntity>>>,
    val audio: List<AudioTrackEntity>
)

class ProjectRepository(private val db: WishyDatabase, val store: BitmapStore) {

    private val projectDao = db.projectDao()
    private val frameDao = db.frameDao()
    private val layerDao = db.layerDao()
    private val audioTrackDao = db.audioTrackDao()

    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    suspend fun createProject(name: String, width: Int, height: Int, fps: Int): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val projectId = projectDao.insert(
                ProjectEntity(
                    name = name, width = width, height = height, fps = fps,
                    createdAt = now, updatedAt = now,
                    uuid = java.util.UUID.randomUUID().toString()
                )
            )
            val frameId = frameDao.insert(FrameEntity(projectId = projectId, position = 0))
            layerDao.insert(
                LayerEntity(frameId = frameId, position = 0, name = "Layer 1", visible = true, opacity = 1f)
            )
            projectId
        }

    suspend fun allProjects(): List<ProjectEntity> = projectDao.all()

    suspend fun projectByUuid(uuid: String): ProjectEntity? = projectDao.byUuid(uuid)

    suspend fun setUuid(id: Long, uuid: String) = projectDao.setUuid(id, uuid)

    suspend fun setFolderName(id: Long, folderName: String?) = projectDao.setFolderName(id, folderName)

    /** Everything needed to write one project to its folder, read in one transaction so it is consistent. */
    suspend fun snapshot(projectId: Long): ProjectSnapshot? = db.withTransaction {
        var project = projectDao.get(projectId) ?: return@withTransaction null
        if (project.uuid.isBlank()) {
            val fresh = java.util.UUID.randomUUID().toString()
            projectDao.setUuid(projectId, fresh)
            project = project.copy(uuid = fresh)
        }
        val frames = frameDao.frames(projectId).map { it to layerDao.forFrame(it.id) }
        ProjectSnapshot(project, frames, audioTrackDao.forProject(projectId))
    }

    /**
     * Creates a project from a folder manifest inside one transaction. [audio] entries are created with the
     * given (already installed) file paths. Returns the new project id and, per frame, the new layer ids
     * in manifest order, so the caller can install the pixel files.
     */
    suspend fun createFromManifest(
        m: org.wishyclip.app.data.folder.ProjectManifest,
        folderName: String,
        audioPaths: Map<String, String>
    ): Pair<Long, List<List<Long>>> = db.withTransaction {
        val projectId = projectDao.insert(
            ProjectEntity(
                name = m.name, width = m.width, height = m.height, fps = m.fps,
                createdAt = m.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = m.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                uuid = m.uuid, folderName = folderName
            )
        )
        val ids = ArrayList<List<Long>>()
        m.frames.forEachIndexed { fi, f ->
            val frameId = frameDao.insert(FrameEntity(projectId = projectId, position = fi, exposureDuration = f.exposure))
            ids.add(f.layers.mapIndexed { li, l ->
                layerDao.insert(
                    LayerEntity(
                        frameId = frameId, position = li, name = l.name, visible = l.visible,
                        opacity = l.opacity, locked = l.locked, blendMode = l.blendMode
                    )
                )
            })
        }
        m.audio.forEach { a ->
            val path = audioPaths[a.file] ?: return@forEach
            audioTrackDao.insert(
                AudioTrackEntity(
                    projectId = projectId, filePath = path, name = a.name, startFrame = a.startFrame,
                    trimStartMs = a.trimStartMs, durationMs = a.durationMs, volume = a.volume
                )
            )
        }
        projectId to ids
    }

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.get(id)

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project)
    }

    /** Called after a project changed, so it can be mirrored to the project folder. Set once by the app. */
    @Volatile var onProjectChanged: ((Long) -> Unit)? = null

    suspend fun touch(id: Long) {
        projectDao.touch(id, System.currentTimeMillis())
        onProjectChanged?.invoke(id)
    }

    suspend fun updateProjectFps(id: Long, fps: Int) {
        projectDao.updateFps(id, fps)
        onProjectChanged?.invoke(id)
    }

    /**
     * Deletes the database rows first (so the project disappears from the list at once), then the files.
     * If the process dies in between, only unreferenced files remain; [purgeOrphanedProjectDirs] removes
     * them on the next start. The reverse order could leave a project whose pixels are gone.
     */
    suspend fun deleteProject(id: Long) {
        projectDao.delete(id)
        withContext(Dispatchers.IO) { store.deleteProject(id) }
    }

    /** Removes project folders that no database row refers to (left by an interrupted delete). */
    suspend fun purgeOrphanedProjectDirs() {
        val known = projectDao.allIds().toSet()
        withContext(Dispatchers.IO) { store.deleteProjectsExcept(known) }
    }

    suspend fun frames(projectId: Long): List<FrameEntity> = frameDao.frames(projectId)

    suspend fun updateFrameExposure(frameId: Long, duration: Int) {
        frameDao.updateExposure(frameId, duration)
        // The owner is looked up lazily by the next touch/startup sync; exposure alone has no project id here.
    }

    suspend fun layers(frameId: Long): List<LayerEntity> = layerDao.forFrame(frameId)

    suspend fun saveLayers(layers: List<LayerEntity>) {
        layerDao.updateAll(layers)
    }

    suspend fun addLayer(frameId: Long, position: Int, name: String): LayerEntity {
        val layer = LayerEntity(frameId = frameId, position = position, name = name, visible = true, opacity = 1f)
        val id = layerDao.insert(layer)
        return layer.copy(id = id)
    }

    suspend fun deleteLayer(projectId: Long, layerId: Long) {
        layerDao.delete(layerId)
        withContext(Dispatchers.IO) { store.deleteLayer(projectId, layerId) }
    }

    /** Inserts a frame at [position]; new layers copy name/visibility/opacity from [templates]. */
    suspend fun insertFrame(
        projectId: Long,
        position: Int,
        templates: List<LayerEntity>
    ): Pair<FrameEntity, List<LayerEntity>> = db.withTransaction {
        val existing = frameDao.frames(projectId).toMutableList()
        val newId = frameDao.insert(FrameEntity(projectId = projectId, position = position))
        val newFrame = FrameEntity(id = newId, projectId = projectId, position = position)
        val at = position.coerceIn(0, existing.size)
        existing.add(at, newFrame)
        frameDao.updateAll(existing.mapIndexed { i, f -> f.copy(position = i) })
        val newLayers = templates.mapIndexed { i, t ->
            val layer = t.copy(id = 0, frameId = newId, position = i)
            layer.copy(id = layerDao.insert(layer))
        }
        Pair(newFrame.copy(position = at), newLayers)
    }

    /** Caller must have persisted pending pixel changes of [source] first. */
    suspend fun duplicateFrame(source: FrameEntity, position: Int): Pair<FrameEntity, List<LayerEntity>> {
        val sourceLayers = layerDao.forFrame(source.id)
        val result = insertFrame(source.projectId, position, sourceLayers)
        try {
            withContext(Dispatchers.IO) {
                sourceLayers.forEachIndexed { i, s ->
                    store.copyLayer(source.projectId, s.id, result.second[i].id)
                }
            }
        } catch (e: Throwable) {
            // A duplicate whose pixels could not be copied would silently be blank. Undo the whole
            // duplication (rows + any files already copied) and let the caller report the failure.
            try {
                withContext(NonCancellable) { deleteFrame(result.first) }
            } catch (_: Exception) {
            }
            throw e
        }
        return result
    }

    /** Replaces [first] and inserts [second] atomically, so a failure cannot leave half a split. */
    suspend fun splitAudioTrack(first: AudioTrackEntity, second: AudioTrackEntity) {
        db.withTransaction {
            audioTrackDao.update(first)
            audioTrackDao.insert(second)
        }
    }

    suspend fun deleteFrame(frame: FrameEntity) {
        val layers = layerDao.forFrame(frame.id)
        val remaining = frameDao.frames(frame.projectId).filter { it.id != frame.id }
        db.withTransaction {
            frameDao.delete(frame.id)
            frameDao.updateAll(remaining.mapIndexed { i, f -> f.copy(position = i) })
        }
        withContext(Dispatchers.IO) {
            layers.forEach { store.deleteLayer(frame.projectId, it.id) }
        }
    }

    suspend fun moveFrame(projectId: Long, from: Int, to: Int) {
        val list = frameDao.frames(projectId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        frameDao.updateAll(list.mapIndexed { i, f -> f.copy(position = i) })
    }

    suspend fun getAudioTracks(projectId: Long): List<AudioTrackEntity> =
        audioTrackDao.forProject(projectId)

    suspend fun addAudioTrack(track: AudioTrackEntity): AudioTrackEntity {
        val id = audioTrackDao.insert(track)
        return track.copy(id = id)
    }

    suspend fun updateAudioTrack(track: AudioTrackEntity) {
        audioTrackDao.update(track)
    }

    suspend fun deleteAudioTrack(id: Long) {
        audioTrackDao.delete(id)
    }
}
