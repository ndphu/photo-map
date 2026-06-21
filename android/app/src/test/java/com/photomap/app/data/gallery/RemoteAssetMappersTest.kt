package com.photomap.app.data.gallery

import com.photomap.app.data.network.RemoteAssetDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteAssetMappersTest {
    @Test
    fun dtoMapsMetadataAndSignedUrlTimestamp() {
        val entity = remoteAsset(thumbnailUrl = "https://signed.example").toEntity(1234L)

        assertEquals("asset-1", entity.id)
        assertEquals("image", entity.mediaType)
        assertEquals(1234L, entity.localCachedAt)
        assertEquals(1234L, entity.updatedFromServerAt)
        assertEquals(1234L, entity.signedUrlUpdatedAt)
    }

    @Test
    fun dtoWithoutSignedUrlsLeavesTimestampNull() {
        assertNull(remoteAsset(thumbnailUrl = null).toEntity(1234L).signedUrlUpdatedAt)
    }

    @Test
    fun previewIsUsedWhenThumbnailIsMissing() {
        val item = remoteAsset(
            thumbnailUrl = null,
            previewUrl = "https://preview.example",
        ).toEntity(1234L).toUiModel()

        assertEquals("https://preview.example", item.thumbnailUrl)
        assertEquals(SignedUrlVariant.PREVIEW.apiValue, item.galleryImageVariant)
    }

    private fun remoteAsset(
        thumbnailUrl: String?,
        previewUrl: String? = null,
    ) = RemoteAssetDto(
        id = "asset-1",
        mediaType = "image",
        mimeType = "image/jpeg",
        originalFilename = "photo.jpg",
        fileSizeBytes = 100L,
        checksumSha256 = "checksum",
        thumbnailKey = "thumb",
        previewKey = null,
        posterFrameKey = null,
        thumbnailUrl = thumbnailUrl,
        previewUrl = previewUrl,
        posterFrameUrl = null,
        takenAt = "2026-06-19T10:00:00Z",
        width = 100,
        height = 80,
        durationMs = null,
        orientation = 1,
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
        uploadedAt = "2026-06-19T10:00:01Z",
        updatedAt = "2026-06-19T10:00:01Z",
    )
}
