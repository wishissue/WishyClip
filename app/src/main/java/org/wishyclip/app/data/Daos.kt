package org.wishyclip.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE projects SET fps = :fps WHERE id = :id")
    suspend fun updateFps(id: Long, fps: Int)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FrameDao {
    @Query("SELECT * FROM frames WHERE projectId = :projectId ORDER BY position ASC")
    suspend fun frames(projectId: Long): List<FrameEntity>

    @Insert
    suspend fun insert(frame: FrameEntity): Long

    @Update
    suspend fun updateAll(frames: List<FrameEntity>)

    @Query("UPDATE frames SET exposureDuration = :duration WHERE id = :id")
    suspend fun updateExposure(id: Long, duration: Int)

    @Query("DELETE FROM frames WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LayerDao {
    @Query("SELECT * FROM layers WHERE frameId = :frameId ORDER BY position ASC")
    suspend fun forFrame(frameId: Long): List<LayerEntity>

    @Insert
    suspend fun insert(layer: LayerEntity): Long

    @Update
    suspend fun updateAll(layers: List<LayerEntity>)

    @Query("DELETE FROM layers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun forProject(projectId: Long): List<AudioTrackEntity>

    @Insert
    suspend fun insert(track: AudioTrackEntity): Long

    @Update
    suspend fun update(track: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun delete(id: Long)
}
