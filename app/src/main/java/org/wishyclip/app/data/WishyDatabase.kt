package org.wishyclip.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, FrameEntity::class, LayerEntity::class, AudioTrackEntity::class],
    version = 4,
    exportSchema = false
)
abstract class WishyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun frameDao(): FrameDao
    abstract fun layerDao(): LayerDao
    abstract fun audioTrackDao(): AudioTrackDao

    companion object {
        /** v2: audio tracks table. Existing projects keep all their data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startFrame` INTEGER NOT NULL DEFAULT 0,
                        `trimStartMs` INTEGER NOT NULL DEFAULT 0,
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `volume` REAL NOT NULL DEFAULT 1.0,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_projectId` ON `audio_tracks` (`projectId`)")
            }
        }

        /** v3: per-layer lock and blend mode. Existing projects keep all their data. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE layers ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE layers ADD COLUMN blendMode TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        /** v4: frame exposure/hold duration. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE frames ADD COLUMN exposureDuration INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): WishyDatabase =
            Room.databaseBuilder(context.applicationContext, WishyDatabase::class.java, "wishyclip.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
