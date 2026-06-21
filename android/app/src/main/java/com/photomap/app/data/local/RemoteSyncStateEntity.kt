package com.photomap.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_sync_state")
data class RemoteSyncStateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "last_change_cursor") val lastChangeCursor: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "is_initial_sync_completed") val isInitialSyncCompleted: Boolean,
)

const val ASSET_METADATA_SYNC_STATE_ID = "asset_metadata"
