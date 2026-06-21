package com.photomap.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalAssetEntity::class,
        RemoteAssetEntity::class,
        RemoteSyncStateEntity::class,
        RemoteAssetPendingOpEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class PhotoMapDatabase : RoomDatabase() {
    abstract fun localAssetDao(): LocalAssetDao
    abstract fun remoteAssetDao(): RemoteAssetDao
    abstract fun remoteSyncStateDao(): RemoteSyncStateDao
    abstract fun remoteAssetPendingOpDao(): RemoteAssetPendingOpDao
}
