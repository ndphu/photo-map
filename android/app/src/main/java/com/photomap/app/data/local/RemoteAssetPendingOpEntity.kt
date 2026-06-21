package com.photomap.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_asset_pending_ops",
    indices = [
        Index(value = ["status", "next_retry_at"]),
        Index(value = ["asset_id"]),
    ],
)
data class RemoteAssetPendingOpEntity(
    @PrimaryKey @ColumnInfo(name = "op_id") val opId: String,
    @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "op_type") val opType: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    val status: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

object RemoteAssetOpType {
    const val FAVORITE = "favorite"
    const val ARCHIVE = "archive"
    const val TRASH = "trash"
    const val RESTORE = "restore"
    const val HARD_DELETE = "hard_delete"
}

object RemoteAssetOpStatus {
    const val PENDING = "pending"
    const val IN_PROGRESS = "in_progress"
    const val FAILED = "failed"
    const val COMPLETED = "completed"
}
