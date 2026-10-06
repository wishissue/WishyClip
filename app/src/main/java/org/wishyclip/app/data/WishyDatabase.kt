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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
