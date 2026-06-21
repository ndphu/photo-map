package com.photomap.app.ui.screens

import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.gallery.toDetailModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetDetailsFormatterTest {
    @Test
    fun formatsFileSizeWithSingleDecimal() {
        assertEquals("1.2 MB", formatFileSize(1_234_567))
    }

    @Test
    fun formatsShortAndLongDuration() {
        assertEquals("1:01", formatDuration(61_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }

    @Test
    fun formatsResolutionAndGps() {
        assertEquals("4032 × 3024", formatResolution(4032, 3024))
        assertEquals("10.123457, 106.123457", formatGps(10.1234567, 106.1234567))
        assertNull(formatResolution(4032, null))
        assertNull(formatGps(10.0, null))
    }

    @Test
    fun optionalSectionsHideMissingValues() {
        val asset = detailAsset()

        assertTrue(locationRows(asset).isEmpty())
        assertTrue(cameraRows(asset).isEmpty())
        assertTrue(propertyRows(asset).none { it.label == "Duration" || it.label == "Orientation" })
    }
}

private fun detailAsset() = AssetDetailDto(
    id = "asset-id",
    mediaType = "image",
    mimeType = "image/jpeg",
    objectKey = "private-object-key",
    thumbnailKey = null,
    previewKey = null,
    posterFrameKey = null,
    originalFilename = "photo.jpg",
    fileSizeBytes = 1_234_567,
    takenAt = null,
    width = 4032,
    height = 3024,
    durationMs = null,
    latitude = null,
    longitude = null,
    city = null,
    isFavorite = false,
    isArchived = false,
    isTrashed = false,
).toDetailModel()
