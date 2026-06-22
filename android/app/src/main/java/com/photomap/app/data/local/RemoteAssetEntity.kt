package com.photomap.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_assets",
    indices = [
        Index(value = ["taken_at", "id"]),
        Index(value = ["media_type", "taken_at"]),
        Index(value = ["is_favorite", "taken_at"]),
        Index(value = ["is_archived", "taken_at"]),
        Index(value = ["is_trashed", "taken_at"]),
    ],
)
data class RemoteAssetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "original_filename") val originalFilename: String?,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long?,
    @ColumnInfo(name = "checksum_sha256") val checksumSha256: String?,
    @ColumnInfo(name = "thumbnail_key") val thumbnailKey: String?,
    @ColumnInfo(name = "preview_key") val previewKey: String?,
    @ColumnInfo(name = "poster_frame_key") val posterFrameKey: String?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "preview_url") val previewUrl: String?,
    @ColumnInfo(name = "poster_frame_url") val posterFrameUrl: String?,
    @ColumnInfo(name = "signed_url_updated_at") val signedUrlUpdatedAt: Long?,
    @ColumnInfo(name = "signed_url_expires_at") val signedUrlExpiresAt: Long?,
    @ColumnInfo(name = "taken_at") val takenAt: String?,
    @ColumnInfo(name = "taken_at_source") val takenAtSource: String? = null,
    @ColumnInfo(name = "timezone_offset_minutes") val timezoneOffsetMinutes: Int? = null,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val orientation: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val country: String?,
    val region: String?,
    val city: String?,
    @ColumnInfo(name = "place_name") val placeName: String?,
    @ColumnInfo(name = "camera_make") val cameraMake: String?,
    @ColumnInfo(name = "camera_model") val cameraModel: String?,
    val software: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "is_trashed") val isTrashed: Boolean,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "local_cached_at") val localCachedAt: Long,
    @ColumnInfo(name = "updated_from_server_at") val updatedFromServerAt: Long,
)
