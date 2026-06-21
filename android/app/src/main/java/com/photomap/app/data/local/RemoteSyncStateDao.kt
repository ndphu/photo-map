package com.photomap.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteSyncStateDao {
    @Query("SELECT * FROM remote_sync_state WHERE id = :id")
    suspend fun getState(id: String): RemoteSyncStateEntity?

    @Query("SELECT * FROM remote_sync_state WHERE id = :id")
    fun observeState(id: String): Flow<RemoteSyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RemoteSyncStateEntity)

    @Query(
        """
        UPDATE remote_sync_state
        SET last_change_cursor = :cursor,
            last_synced_at = :lastSyncedAt,
            last_error = CASE WHEN :clearError THEN NULL ELSE last_error END,
            is_initial_sync_completed = 1
        WHERE id = :id
        """,
    )
    suspend fun updateCursor(id: String, cursor: Long, lastSyncedAt: Long, clearError: Boolean)

    @Query("UPDATE remote_sync_state SET last_error = :lastError WHERE id = :id")
    suspend fun updateError(id: String, lastError: String)

    @Query("DELETE FROM remote_sync_state")
    suspend fun clearAll()
}
