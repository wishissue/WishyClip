package org.wishyclip.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, FrameEntity::class, LayerEntity::class, AudioTrackEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WishyDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun frameDao(): FrameDao
    abstract fun layerDao(): LayerDao
    abstract fun audioTrackDao(): AudioTrackDao

    companion object {
        fun build(context: Context): WishyDatabase =
            Room.databaseBuilder(context.applicationContext, WishyDatabase::class.java, "wishyclip.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
