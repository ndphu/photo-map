package com.photomap.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalAssetEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PhotoMapDatabase : RoomDatabase() {
    abstract fun localAssetDao(): LocalAssetDao
}
