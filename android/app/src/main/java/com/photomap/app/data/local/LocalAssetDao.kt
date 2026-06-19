package com.photomap.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAssetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiscovered(assets: List<LocalAssetEntity>)

    @Query("SELECT * FROM local_assets WHERE syncStatus IN (:statuses) ORDER BY takenAt ASC LIMIT :limit")
    suspend fun pending(statuses: List<String>, limit: Int): List<LocalAssetEntity>

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncStatus = :status")
    suspend fun countByStatusOnce(status: String): Int

    @Query("UPDATE local_assets SET syncStatus = :status, lastError = :error WHERE localAssetId = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :status, remoteAssetId = :remoteAssetId,
            lastError = NULL, lastSyncedAt = :syncedAt
        WHERE localAssetId = :id
        """,
    )
    suspend fun markUploaded(id: String, status: String, remoteAssetId: String, syncedAt: Long)

    @Query("UPDATE local_assets SET syncStatus = :pending, lastError = NULL WHERE syncStatus = :failed")
    suspend fun retryFailed(failed: String = SyncStatus.FAILED, pending: String = SyncStatus.PENDING)

    @Query("UPDATE local_assets SET syncStatus = :pending WHERE syncStatus = :uploading")
    suspend fun resetInterruptedUploads(
        uploading: String = SyncStatus.UPLOADING,
        pending: String = SyncStatus.PENDING,
    )

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncStatus = :status")
    fun countByStatus(status: String): Flow<Int>
}
