package com.photomap.app.data.gallery

import com.photomap.app.data.local.RemoteAssetEntity
import com.photomap.app.data.network.RemoteAssetDto

fun RemoteAssetDto.toEntity(nowMillis: Long): RemoteAssetEntity {
    val hasSignedUrl = thumbnailUrl != null || previewUrl != null || posterFrameUrl != null
    return RemoteAssetEntity(
        id = id,
        mediaType = mediaType,
        mimeType = mimeType,
        originalFilename = originalFilename,
        fileSizeBytes = fileSizeBytes,
        checksumSha256 = checksumSha256,
        thumbnailKey = thumbnailKey,
        previewKey = previewKey,
        posterFrameKey = posterFrameKey,
        thumbnailUrl = thumbnailUrl,
        previewUrl = previewUrl,
        posterFrameUrl = posterFrameUrl,
        signedUrlUpdatedAt = nowMillis.takeIf { hasSignedUrl },
        signedUrlExpiresAt = signedUrlExpiresAt,
        takenAt = takenAt,
        takenAtSource = takenAtSource,
        timezoneOffsetMinutes = timezoneOffsetMinutes,
        width = width,
        height = height,
        durationMs = durationMs,
        orientation = orientation,
        latitude = latitude,
        longitude = longitude,
        country = country,
        region = region,
        city = city,
        placeName = placeName,
        cameraMake = cameraMake,
        cameraModel = cameraModel,
        software = software,
        isFavorite = isFavorite,
        isArchived = isArchived,
        isTrashed = isTrashed,
        uploadedAt = uploadedAt,
        updatedAt = updatedAt,
        localCachedAt = nowMillis,
        updatedFromServerAt = nowMillis,
    )
}

fun RemoteAssetEntity.toUiModel() = AssetUiModel(
    id = id,
    mediaType = mediaType,
    mimeType = mimeType,
    thumbnailUrl = thumbnailUrl ?: previewUrl,
    previewUrl = previewUrl,
    takenAt = takenAt,
    width = width,
    height = height,
    durationMs = durationMs,
    isFavorite = isFavorite,
    galleryImageVariant = if (thumbnailUrl != null) SignedUrlVariant.THUMBNAIL.apiValue else SignedUrlVariant.PREVIEW.apiValue,
)
