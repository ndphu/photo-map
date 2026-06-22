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

    @Query(
        """
        SELECT * FROM local_assets
        WHERE syncStatus IN (:statuses)
          AND (nextRetryAt IS NULL OR nextRetryAt <= :now)
        ORDER BY takenAt ASC
        LIMIT :limit
        """,
    )
    suspend fun readyForUpload(statuses: List<String>, now: Long, limit: Int): List<LocalAssetEntity>

    @Query(
        """
        SELECT COUNT(*) FROM local_assets
        WHERE syncStatus IN (:statuses)
          AND (nextRetryAt IS NULL OR nextRetryAt <= :now)
        """,
    )
    suspend fun countReadyForUpload(statuses: List<String>, now: Long): Int

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncStatus = :status")
    suspend fun countByStatusOnce(status: String): Int

    @Query("UPDATE local_assets SET syncStatus = :status, lastError = :error WHERE localAssetId = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query(
        """
        UPDATE local_assets
        SET uploadSessionId = :uploadSessionId
        WHERE localAssetId = :id
        """,
    )
    suspend fun saveUploadSession(id: String, uploadSessionId: String)

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :status, uploadAttemptCount = uploadAttemptCount + 1,
            lastError = NULL, nextRetryAt = NULL
        WHERE localAssetId = :id
        """,
    )
    suspend fun beginUploadAttempt(id: String, status: String = SyncStatus.UPLOADING)

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :status, lastError = :error, nextRetryAt = :nextRetryAt
        WHERE localAssetId = :id
        """,
    )
    suspend fun markFailed(
        id: String,
        error: String,
        nextRetryAt: Long?,
        status: String = SyncStatus.FAILED,
    )

    @Query("UPDATE local_assets SET uploadSessionId = NULL WHERE localAssetId = :id")
    suspend fun clearUploadSession(id: String)

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :status, remoteAssetId = :remoteAssetId,
            lastError = NULL, lastSyncedAt = :syncedAt, nextRetryAt = NULL,
            metadataBackfillStatus = :metadataStatus,
            metadataBackfilledAt = CASE WHEN :metadataStatus = 'completed' THEN :syncedAt ELSE NULL END,
            metadataBackfillError = NULL
        WHERE localAssetId = :id
        """,
    )
    suspend fun markUploaded(
        id: String,
        status: String,
        remoteAssetId: String,
        syncedAt: Long,
        metadataStatus: String,
    )

    @Query(
        """
        SELECT * FROM local_assets
        WHERE syncStatus = 'uploaded'
          AND remoteAssetId IS NOT NULL
          AND metadataBackfillStatus = 'pending'
        ORDER BY takenAt ASC, localAssetId ASC
        LIMIT :limit
        """,
    )
    suspend fun metadataBackfillCandidates(limit: Int): List<LocalAssetEntity>

    @Query(
        """
        UPDATE local_assets
        SET metadataBackfillStatus = :status,
            metadataBackfilledAt = :completedAt,
            metadataBackfillError = NULL
        WHERE localAssetId = :id
        """,
    )
    suspend fun markMetadataBackfillCompleted(
        id: String,
        completedAt: Long,
        status: String = MetadataBackfillStatus.COMPLETED,
    )

    @Query(
        """
        UPDATE local_assets
        SET metadataBackfillStatus = :status, metadataBackfillError = :error
        WHERE localAssetId = :id
        """,
    )
    suspend fun markMetadataBackfillIssue(id: String, status: String, error: String)

    @Query(
        """
        UPDATE local_assets
        SET metadataBackfillStatus = :pending, metadataBackfillError = NULL
        WHERE metadataBackfillStatus IN (:failed, :skipped)
        """,
    )
    suspend fun retryMetadataBackfill(
        pending: String = MetadataBackfillStatus.PENDING,
        failed: String = MetadataBackfillStatus.FAILED,
        skipped: String = MetadataBackfillStatus.SKIPPED,
    )

    @Query("SELECT COUNT(*) FROM local_assets WHERE metadataBackfillStatus = :status AND syncStatus = 'uploaded'")
    fun countMetadataBackfillStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM local_assets WHERE metadataBackfillStatus = 'pending' AND syncStatus = 'uploaded'")
    suspend fun countPendingMetadataBackfillOnce(): Int

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :pending, lastError = NULL, nextRetryAt = NULL,
            uploadAttemptCount = 0
        WHERE syncStatus = :failed
        """,
    )
    suspend fun retryFailed(failed: String = SyncStatus.FAILED, pending: String = SyncStatus.PENDING)

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :pending, lastError = NULL, nextRetryAt = NULL,
            uploadAttemptCount = 0
        WHERE syncStatus = :skipped AND mediaType = 'video'
        """,
    )
    suspend fun retrySkippedVideos(
        skipped: String = SyncStatus.SKIPPED,
        pending: String = SyncStatus.PENDING,
    )

    @Query("UPDATE local_assets SET syncStatus = :pending WHERE syncStatus = :uploading")
    suspend fun resetInterruptedUploads(
        uploading: String = SyncStatus.UPLOADING,
        pending: String = SyncStatus.PENDING,
    )

    @Query(
        """
        UPDATE local_assets
        SET syncStatus = :pending,
            remoteAssetId = NULL,
            lastError = NULL,
            lastSyncedAt = NULL,
            uploadSessionId = NULL,
            uploadAttemptCount = 0,
            nextRetryAt = NULL,
            metadataBackfillStatus = 'pending',
            metadataBackfilledAt = NULL,
            metadataBackfillError = NULL
        """,
    )
    suspend fun resetForBackendChange(pending: String = SyncStatus.PENDING)

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncStatus = :status")
    fun countByStatus(status: String): Flow<Int>
}
