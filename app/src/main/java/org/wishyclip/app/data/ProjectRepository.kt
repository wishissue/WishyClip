package org.wishyclip.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
                    createdAt = now, updatedAt = now
                )
            )
            val frameId = frameDao.insert(FrameEntity(projectId = projectId, position = 0))
            layerDao.insert(
                LayerEntity(frameId = frameId, position = 0, name = "Layer 1", visible = true, opacity = 1f)
            )
            projectId
        }

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.get(id)

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project)
    }

    suspend fun touch(id: Long) {
        projectDao.touch(id, System.currentTimeMillis())
    }

    suspend fun updateProjectFps(id: Long, fps: Int) {
        projectDao.updateFps(id, fps)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.delete(id)
        withContext(Dispatchers.IO) { store.deleteProject(id) }
    }

    suspend fun frames(projectId: Long): List<FrameEntity> = frameDao.frames(projectId)

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
            val layer = LayerEntity(
                frameId = newId, position = i, name = t.name, visible = t.visible, opacity = t.opacity
            )
            layer.copy(id = layerDao.insert(layer))
        }
        Pair(newFrame.copy(position = at), newLayers)
    }

    /** Caller must have persisted pending pixel changes of [source] first. */
    suspend fun duplicateFrame(source: FrameEntity, position: Int): Pair<FrameEntity, List<LayerEntity>> {
        val sourceLayers = layerDao.forFrame(source.id)
        val result = insertFrame(source.projectId, position, sourceLayers)
        withContext(Dispatchers.IO) {
            sourceLayers.forEachIndexed { i, s ->
                store.copyLayer(source.projectId, s.id, result.second[i].id)
            }
        }
        return result
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
