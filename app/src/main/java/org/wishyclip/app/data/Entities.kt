package org.wishyclip.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Project > Frames > Layers. Room stores metadata only; pixels live in PNG files (see BitmapStore). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "frames",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class FrameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val position: Int,
    @ColumnInfo(defaultValue = "1") val exposureDuration: Int = 1
)

@Entity(
    tableName = "layers",
    foreignKeys = [
        ForeignKey(
            entity = FrameEntity::class,
            parentColumns = ["id"],
            childColumns = ["frameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("frameId")]
)
data class LayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frameId: Long,
    val position: Int,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(defaultValue = "'NORMAL'") val blendMode: String = "NORMAL"
)

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val filePath: String,
    val name: String,
    val startFrame: Int = 0,
    val trimStartMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f
)
