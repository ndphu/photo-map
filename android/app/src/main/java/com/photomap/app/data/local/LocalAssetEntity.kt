package com.photomap.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_assets")
data class LocalAssetEntity(
    @PrimaryKey val localAssetId: String,
    val uri: String,
    val mediaType: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val takenAt: Long?,
    val modifiedAt: Long?,
    val syncStatus: String,
    val remoteAssetId: String?,
    val lastError: String?,
    val lastSyncedAt: Long?,
    val uploadSessionId: String? = null,
    val uploadAttemptCount: Int = 0,
    val nextRetryAt: Long? = null,
    val metadataBackfillStatus: String = MetadataBackfillStatus.PENDING,
    val metadataBackfilledAt: Long? = null,
    val metadataBackfillError: String? = null,
)

object SyncStatus {
    const val PENDING = "pending"
    const val UPLOADING = "uploading"
    const val UPLOADED = "uploaded"
    const val FAILED = "failed"
    const val SKIPPED = "skipped"
}

object MetadataBackfillStatus {
    const val PENDING = "pending"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"
    const val FAILED = "failed"
}
