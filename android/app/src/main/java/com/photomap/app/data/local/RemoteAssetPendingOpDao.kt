package com.photomap.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteAssetPendingOpDao {
    @Query("SELECT COUNT(*) FROM remote_asset_pending_ops WHERE status = 'failed'")
    fun observeFailedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: RemoteAssetPendingOpEntity)

    @Query(
        """
        SELECT * FROM remote_asset_pending_ops
        WHERE status = 'pending'
           OR (status = 'failed' AND next_retry_at IS NOT NULL AND next_retry_at <= :nowMillis)
        ORDER BY created_at ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingReadyOps(nowMillis: Long, limit: Int): List<RemoteAssetPendingOpEntity>

    @Query(
        """
        UPDATE remote_asset_pending_ops
        SET status = 'in_progress', updated_at = :updatedAt
        WHERE op_id = :opId
        """,
    )
    suspend fun markInProgress(opId: String, updatedAt: Long)

    @Query(
        """
        UPDATE remote_asset_pending_ops
        SET status = 'failed', last_error = :error, next_retry_at = :nextRetryAt,
            attempt_count = :attemptCount, updated_at = :updatedAt
        WHERE op_id = :opId
        """,
    )
    suspend fun markFailed(
        opId: String,
        error: String,
        nextRetryAt: Long?,
        attemptCount: Int,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE remote_asset_pending_ops
        SET status = 'completed', last_error = NULL, next_retry_at = NULL, updated_at = :updatedAt
        WHERE op_id = :opId
        """,
    )
    suspend fun markCompleted(opId: String, updatedAt: Long)

    @Query("DELETE FROM remote_asset_pending_ops WHERE status = 'completed' AND updated_at < :cutoff")
    suspend fun deleteCompletedOlderThan(cutoff: Long)

    @Query("DELETE FROM remote_asset_pending_ops")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM remote_asset_pending_ops
        WHERE asset_id = :assetId AND op_type = :opType AND status != 'completed'
        """,
    )
    suspend fun deleteActiveByAssetAndType(assetId: String, opType: String)

    @Query(
        """
        DELETE FROM remote_asset_pending_ops
        WHERE asset_id = :assetId AND op_type IN ('trash', 'restore') AND status != 'completed'
        """,
    )
    suspend fun deleteActiveTrashRestore(assetId: String)

    @Query("DELETE FROM remote_asset_pending_ops WHERE asset_id = :assetId AND status != 'completed'")
    suspend fun deleteAllActiveForAsset(assetId: String)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM remote_asset_pending_ops
            WHERE asset_id = :assetId AND op_type = 'hard_delete' AND status != 'completed'
        )
        """,
    )
    suspend fun hasActiveHardDelete(assetId: String): Boolean

    @Query(
        """
        UPDATE remote_asset_pending_ops
        SET status = 'pending', next_retry_at = NULL, last_error = NULL, updated_at = :updatedAt
        WHERE status = 'failed'
        """,
    )
    suspend fun retryFailed(updatedAt: Long)

    @Query(
        """
        UPDATE remote_asset_pending_ops
        SET status = 'pending', updated_at = :updatedAt
        WHERE status = 'in_progress'
        """,
    )
    suspend fun resetInterrupted(updatedAt: Long)
}
