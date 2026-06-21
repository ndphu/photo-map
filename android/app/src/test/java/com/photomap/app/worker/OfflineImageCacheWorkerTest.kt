package com.photomap.app.worker

import com.photomap.app.data.cache.CloudImageVariant
import com.photomap.app.data.local.RemoteAssetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineImageCacheWorkerTest {
    @Test
    fun previewsAreQueuedBeforeThumbnailsInSourceOrder() {
        val tasks = buildOfflineCacheTasks(
            listOf(cacheAsset("old"), cacheAsset("new")),
        )

        assertEquals(
            listOf(
                "old" to CloudImageVariant.PREVIEW,
                "new" to CloudImageVariant.PREVIEW,
                "old" to CloudImageVariant.THUMBNAIL,
                "new" to CloudImageVariant.THUMBNAIL,
            ),
            tasks.map { it.assetId to it.cloudVariant },
        )
    }
}

private fun cacheAsset(id: String) = RemoteAssetEntity(
    id = id,
    mediaType = "image",
    mimeType = "image/jpeg",
    originalFilename = "$id.jpg",
    fileSizeBytes = 1,
    checksumSha256 = null,
    thumbnailKey = "thumb-$id",
    previewKey = "preview-$id",
    posterFrameKey = null,
    thumbnailUrl = "https://signed/thumb-$id",
    previewUrl = "https://signed/preview-$id",
    posterFrameUrl = null,
    signedUrlUpdatedAt = null,
    signedUrlExpiresAt = null,
    takenAt = null,
    width = null,
    height = null,
    durationMs = null,
    orientation = null,
    latitude = null,
    longitude = null,
    country = null,
    region = null,
    city = null,
    placeName = null,
    cameraMake = null,
    cameraModel = null,
    isFavorite = false,
    isArchived = false,
    isTrashed = false,
    uploadedAt = null,
    updatedAt = null,
    localCachedAt = 0,
    updatedFromServerAt = 0,
)
