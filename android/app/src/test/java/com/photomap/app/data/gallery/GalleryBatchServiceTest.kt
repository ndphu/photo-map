package com.photomap.app.data.gallery

import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.repository.AssetRemoteDataSource
import com.photomap.app.data.repository.AssetRepository
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryBatchServiceTest {
    @Test
    fun allSuccessUsesBoundedConcurrency() = runTest {
        val remote = ConcurrentAssetRemoteDataSource()
        val service = GalleryBatchService(
            AssetRepository(remote, GalleryInvalidator()),
            EmptyBatchAlbumStore,
        )

        val result = service.execute(
            assetIds = (1..12).map { "asset-$it" }.toSet(),
            action = GalleryBatchAction.Favorite,
            onProgress = { _, _ -> },
        )

        assertEquals(12, result.succeeded)
        assertTrue(result.failedIds.isEmpty())
        assertTrue(remote.maxConcurrent.get() in 2..4)
    }

    @Test
    fun partialFailureReturnsOnlyFailedIds() = runTest {
        val remote = ConcurrentAssetRemoteDataSource(failedIds = setOf("asset-2", "asset-4"))
        val service = GalleryBatchService(
            AssetRepository(remote, GalleryInvalidator()),
            EmptyBatchAlbumStore,
        )

        val result = service.execute(
            assetIds = (1..4).map { "asset-$it" }.toSet(),
            action = GalleryBatchAction.Archive,
            onProgress = { _, _ -> },
        )

        assertEquals(2, result.succeeded)
        assertEquals(setOf("asset-2", "asset-4"), result.failedIds)
    }
}

private class ConcurrentAssetRemoteDataSource(
    private val failedIds: Set<String> = emptySet(),
) : AssetRemoteDataSource {
    private val active = AtomicInteger(0)
    val maxConcurrent = AtomicInteger(0)

    override suspend fun detail(id: String): AssetDetailDto = testBatchAsset(id)

    override suspend fun readUrl(id: String, variant: String): String = error("unused")

    override suspend fun favorite(id: String, favorite: Boolean): AssetDetailDto = mutate(id)

    override suspend fun archive(id: String, archived: Boolean): AssetDetailDto = mutate(id)

    override suspend fun trash(id: String): AssetDetailDto = mutate(id)

    override suspend fun restore(id: String): AssetDetailDto = error("unused")

    override suspend fun delete(id: String) = Unit

    private suspend fun mutate(id: String): AssetDetailDto {
        val current = active.incrementAndGet()
        maxConcurrent.updateAndGet { maxOf(it, current) }
        try {
            delay(10)
            if (id in failedIds) throw IOException("failed")
            return testBatchAsset(id)
        } finally {
            active.decrementAndGet()
        }
    }
}

private object EmptyBatchAlbumStore : AlbumStore {
    override suspend fun listAlbums(): List<AlbumDto> = emptyList()
    override suspend fun getAlbum(id: String): AlbumDto = error("unused")
    override suspend fun createAlbum(name: String, description: String?): AlbumDto = error("unused")
    override suspend fun updateAlbum(id: String, name: String, description: String?): AlbumDto = error("unused")
    override suspend fun deleteAlbum(id: String) = Unit
    override suspend fun listAssets(albumId: String): List<AssetUiModel> = emptyList()
    override suspend fun addAsset(albumId: String, assetId: String) = Unit
    override suspend fun removeAsset(albumId: String, assetId: String) = Unit
}

private fun testBatchAsset(id: String) = AssetDetailDto(
    id = id,
    mediaType = "image",
    mimeType = "image/jpeg",
    objectKey = "users/user/$id.jpg",
    thumbnailKey = null,
    previewKey = null,
    posterFrameKey = null,
    originalFilename = "$id.jpg",
    fileSizeBytes = 100,
    takenAt = null,
    width = 100,
    height = 100,
    durationMs = null,
    latitude = null,
    longitude = null,
    city = null,
    isFavorite = false,
    isArchived = false,
    isTrashed = false,
)
