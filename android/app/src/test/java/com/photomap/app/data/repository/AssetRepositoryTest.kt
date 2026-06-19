package com.photomap.app.data.repository

import com.photomap.app.data.gallery.GalleryInvalidator
import com.photomap.app.data.network.AssetDetailDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetRepositoryTest {
    @Test
    fun successfulMutationInvalidatesGallery() = runTest {
        val invalidator = GalleryInvalidator()
        val repository = AssetRepository(RepositoryFakeAssetRemote(), invalidator)

        repository.setFavorite("asset", true)

        assertEquals(1L, invalidator.version.value)
    }
}

private class RepositoryFakeAssetRemote : AssetRemoteDataSource {
    private var asset = repositoryAsset()

    override suspend fun detail(id: String) = asset
    override suspend fun readUrl(id: String, variant: String) = "https://signed.example/preview"
    override suspend fun favorite(id: String, favorite: Boolean) = asset.copy(isFavorite = favorite).also { asset = it }
    override suspend fun archive(id: String, archived: Boolean) = asset.copy(isArchived = archived).also { asset = it }
    override suspend fun trash(id: String) = asset.copy(isTrashed = true).also { asset = it }
    override suspend fun restore(id: String) = asset.copy(isTrashed = false).also { asset = it }
    override suspend fun delete(id: String) = Unit
}

private fun repositoryAsset() = AssetDetailDto(
    id = "asset",
    mediaType = "image",
    mimeType = "image/jpeg",
    objectKey = "original",
    thumbnailKey = null,
    previewKey = null,
    posterFrameKey = null,
    originalFilename = null,
    fileSizeBytes = 1,
    takenAt = null,
    width = null,
    height = null,
    durationMs = null,
    latitude = null,
    longitude = null,
    city = null,
    isFavorite = false,
    isArchived = false,
    isTrashed = false,
)
