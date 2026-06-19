package com.photomap.app.data.gallery

import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.repository.AssetRepository
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException

sealed interface GalleryBatchAction {
    data object Favorite : GalleryBatchAction
    data object Archive : GalleryBatchAction
    data object Trash : GalleryBatchAction
    data class AddToAlbum(val albumId: String) : GalleryBatchAction
}

data class GalleryBatchResult(
    val total: Int,
    val succeeded: Int,
    val failedIds: Set<String>,
)

interface GalleryBatchActions {
    suspend fun listAlbums(): List<AlbumDto>

    suspend fun execute(
        assetIds: Set<String>,
        action: GalleryBatchAction,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): GalleryBatchResult
}

class GalleryBatchService(
    private val assetRepository: AssetRepository,
    private val albumRepository: AlbumStore,
) : GalleryBatchActions {
    override suspend fun listAlbums(): List<AlbumDto> = albumRepository.listAlbums()

    override suspend fun execute(
        assetIds: Set<String>,
        action: GalleryBatchAction,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): GalleryBatchResult = supervisorScope {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val completed = AtomicInteger(0)
        val failedIds = ConcurrentLinkedQueue<String>()
        val total = assetIds.size

        assetIds.map { assetId ->
            async {
                val succeeded = semaphore.withPermit { runAction(assetId, action) }
                if (!succeeded) failedIds.add(assetId)
                onProgress(completed.incrementAndGet(), total)
            }
        }.awaitAll()

        if (action !is GalleryBatchAction.AddToAlbum) {
            assetRepository.invalidateGallery()
        }

        GalleryBatchResult(
            total = total,
            succeeded = total - failedIds.size,
            failedIds = failedIds.toSet(),
        )
    }

    private suspend fun runAction(assetId: String, action: GalleryBatchAction): Boolean = try {
        when (action) {
            GalleryBatchAction.Favorite -> assetRepository.setFavorite(assetId, true, invalidateGallery = false)
            GalleryBatchAction.Archive -> assetRepository.setArchived(assetId, true, invalidateGallery = false)
            GalleryBatchAction.Trash -> assetRepository.trash(assetId, invalidateGallery = false)
            is GalleryBatchAction.AddToAlbum -> albumRepository.addAsset(action.albumId, assetId)
        }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        action is GalleryBatchAction.AddToAlbum && error.code() == HTTP_CONFLICT
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 4
        const val HTTP_CONFLICT = 409
    }
}
