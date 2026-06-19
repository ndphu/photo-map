package com.photomap.app.data.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val accessToken: String,
    val user: UserDto,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    val deviceName: String,
    val platform: String = "android",
    val deviceFingerprint: String,
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
    val id: String,
    val userId: String,
    val deviceName: String,
    val platform: String,
    val deviceFingerprint: String,
)

@JsonClass(generateAdapter = true)
data class CreateUploadSessionRequest(
    val deviceId: String,
    val localAssetId: String,
    val mediaType: String,
    val mimeType: String,
    val originalFilename: String,
    val fileSizeBytes: Long,
    val expectedChecksumSha256: String?,
)

@JsonClass(generateAdapter = true)
data class UploadSessionResponse(
    val status: String,
    val asset: UploadAssetDto? = null,
    val session: UploadSessionDto? = null,
    val uploadUrls: UploadUrlsDto? = null,
)

@JsonClass(generateAdapter = true)
data class UploadAssetDto(
    val id: String,
)

@JsonClass(generateAdapter = true)
data class UploadSessionDto(
    val id: String,
    val status: String,
    val bucket: String,
    val objectKey: String,
    val thumbnailKey: String,
    val previewKey: String,
    val posterFrameKey: String?,
    val expiresAt: String,
)

@JsonClass(generateAdapter = true)
data class UpdateUploadSessionStatusRequest(
    val status: String,
    val errorMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class UploadSessionStatusResponse(
    val status: String,
    val errorMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class UploadUrlsDto(
    val original: String,
    val thumbnail: String,
    val preview: String,
    val posterFrame: String?,
)

@JsonClass(generateAdapter = true)
data class CompleteUploadRequest(
    val checksumSha256: String,
    val takenAt: String?,
    val takenAtSource: String?,
    val timezoneOffsetMinutes: Int?,
    val width: Int?,
    val height: Int?,
    val orientation: Int?,
    val durationMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val cameraMake: String?,
    val cameraModel: String?,
    val software: String?,
    val localCreatedAt: String?,
    val localModifiedAt: String?,
)

@JsonClass(generateAdapter = true)
data class CompleteUploadResponse(
    val assetId: String,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class AssetListResponse(
    val items: List<AssetItemDto>,
    val nextCursor: String?,
)

@JsonClass(generateAdapter = true)
data class AssetItemDto(
    val id: String,
    val mediaType: String,
    val mimeType: String,
    val thumbnailKey: String?,
    val previewKey: String?,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val takenAt: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val isFavorite: Boolean,
)

@JsonClass(generateAdapter = true)
data class AssetDetailDto(
    val id: String,
    val mediaType: String,
    val mimeType: String,
    val objectKey: String,
    val thumbnailKey: String?,
    val previewKey: String?,
    val posterFrameKey: String?,
    val originalFilename: String?,
    val fileSizeBytes: Long,
    val takenAt: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val isTrashed: Boolean,
)

@JsonClass(generateAdapter = true)
data class ReadUrlResponse(val url: String)

@JsonClass(generateAdapter = true)
data class FavoriteRequest(val isFavorite: Boolean)

@JsonClass(generateAdapter = true)
data class ArchiveRequest(val isArchived: Boolean)

@JsonClass(generateAdapter = true)
data class AlbumDto(
    val id: String,
    val name: String,
    val description: String?,
    val coverAssetId: String?,
    val isArchived: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class AlbumListResponse(val items: List<AlbumDto>)

@JsonClass(generateAdapter = true)
data class CreateAlbumRequest(val name: String, val description: String?)

@JsonClass(generateAdapter = true)
data class UpdateAlbumRequest(
    val name: String? = null,
    val description: String? = null,
    val coverAssetId: String? = null,
    val isArchived: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AddAssetToAlbumRequest(val assetId: String, val sortOrder: Long? = null)
