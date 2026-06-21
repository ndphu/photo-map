package com.photomap.app.data.gallery

import androidx.paging.PagingData
import androidx.room.withTransaction
import com.photomap.app.data.local.ASSET_METADATA_SYNC_STATE_ID
import com.photomap.app.data.local.PhotoMapDatabase
import com.photomap.app.data.local.RemoteAssetDao
import com.photomap.app.data.local.RemoteAssetEntity
import com.photomap.app.data.local.RemoteSyncStateDao
import com.photomap.app.data.local.RemoteSyncStateEntity
import com.photomap.app.data.network.AssetChangesResponseDto
import com.photomap.app.data.network.PhotoMapApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class GalleryInvalidator {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    fun invalidate() {
        _version.value += 1L
    }
}

interface GalleryPager {
    val invalidationVersion: StateFlow<Long>
    fun getGalleryPager(filter: GalleryFilter): Flow<PagingData<AssetUiModel>>
}

data class AssetMetadataSyncStatus(
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
)

interface AssetMetadataSyncer {
    val metadataSyncStatus: StateFlow<AssetMetadataSyncStatus>
    suspend fun syncAssetMetadata(force: Boolean): Result<Unit>
    suspend fun clearRemoteReplica()
    suspend fun refreshSignedUrl(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit>
}

interface AssetDetailStore : AssetViewerSequence {
    fun observeAssetDetail(assetId: String): Flow<AssetDetailModel?>
    suspend fun refreshSignedUrl(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit>
}

fun interface AssetChangesRemoteDataSource {
    suspend fun load(cursor: Long, limit: Int): AssetChangesResponseDto
}

class RetrofitAssetChangesRemoteDataSource(
    private val api: PhotoMapApi,
) : AssetChangesRemoteDataSource {
    override suspend fun load(cursor: Long, limit: Int): AssetChangesResponseDto =
        api.getAssetChanges(cursor, limit)
}

class GalleryRepository(
    private val database: PhotoMapDatabase,
    private val remoteAssetDao: RemoteAssetDao,
    private val syncStateDao: RemoteSyncStateDao,
    private val remoteDataSource: AssetChangesRemoteDataSource,
    private val api: PhotoMapApi,
    invalidator: GalleryInvalidator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onMetadataSyncCompleted: () -> Unit = {},
) : GalleryPager, AssetMetadataSyncer, AssetDetailStore {
    override val invalidationVersion: StateFlow<Long> = invalidator.version

    private val syncMutex = Mutex()
    private val signedUrlRefreshes = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()
    private val _metadataSyncStatus = MutableStateFlow(AssetMetadataSyncStatus())
    override val metadataSyncStatus: StateFlow<AssetMetadataSyncStatus> =
        _metadataSyncStatus.asStateFlow()

    override fun getGalleryPager(filter: GalleryFilter): Flow<PagingData<AssetUiModel>> =
        remoteAssetDao.observeGalleryAssets(
            mediaType = filter.mediaType.apiValue,
            favorite = true.takeIf { filter.favoriteOnly },
            archived = filter.archived,
            trashed = filter.trashed,
            city = filter.city,
            fromDate = filter.from,
            toDate = filter.to,
        ).map { assets -> PagingData.from(assets.map(RemoteAssetEntity::toUiModel)) }

    override fun observeViewerAssets(): Flow<List<ViewerAssetSummary>> =
        remoteAssetDao.observeViewerAssets().map { assets ->
            assets.map { asset ->
                ViewerAssetSummary(
                    id = asset.id,
                    mediaType = asset.mediaType,
                    originalFilename = asset.originalFilename,
                    thumbnailUrl = asset.thumbnailUrl,
                    previewUrl = asset.previewUrl,
                )
            }
        }

    override fun observeAssetDetail(assetId: String): Flow<AssetDetailModel?> =
        remoteAssetDao.observeAsset(assetId).map { it?.toDetailModel() }

    override suspend fun syncAssetMetadata(force: Boolean): Result<Unit> = syncMutex.withLock {
        _metadataSyncStatus.value = AssetMetadataSyncStatus(isSyncing = true)
        try {
            var state = syncStateDao.getState(ASSET_METADATA_SYNC_STATE_ID)
            if (state == null) {
                state = emptySyncState()
                syncStateDao.upsert(state)
            }

            var cursor = state.lastChangeCursor
            var pageCount = 0
            val maxPages = if (force) MAX_FORCE_SYNC_PAGES else MAX_BACKGROUND_SYNC_PAGES
            var hasMore: Boolean
            do {
                val response = remoteDataSource.load(cursor, ASSET_CHANGES_PAGE_SIZE)
                val changes = response.items.sortedBy { it.changeId }
                val nextCursor = changes.lastOrNull()?.changeId ?: cursor
                require(nextCursor >= cursor) { "Asset change cursor moved backwards" }
                require(!response.hasMore || nextCursor > cursor) { "Asset change feed made no progress" }
                val syncedAt = nowMillis()

                database.withTransaction {
                    changes.forEach { change ->
                        when (change.changeType) {
                            CHANGE_UPSERT, CHANGE_TRASH, CHANGE_RESTORE -> {
                                val asset = requireNotNull(change.asset) {
                                    "Non-delete asset change has no snapshot"
                                }
                                remoteAssetDao.upsert(asset.toEntity(syncedAt))
                            }

                            CHANGE_DELETE -> remoteAssetDao.deleteById(change.assetId)
                            else -> error("Unknown asset change type: ${change.changeType}")
                        }
                    }
                    syncStateDao.upsert(
                        RemoteSyncStateEntity(
                            id = ASSET_METADATA_SYNC_STATE_ID,
                            lastChangeCursor = nextCursor,
                            lastSyncedAt = syncedAt,
                            lastError = null,
                            isInitialSyncCompleted = true,
                        ),
                    )
                }

                cursor = nextCursor
                hasMore = response.hasMore
                pageCount += 1
            } while (hasMore && pageCount < maxPages)

            _metadataSyncStatus.value = AssetMetadataSyncStatus()
            runCatching { onMetadataSyncCompleted() }
            Result.success(Unit)
        } catch (error: CancellationException) {
            _metadataSyncStatus.value = AssetMetadataSyncStatus()
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error::class.simpleName ?: "Sync failed"
            runCatching { syncStateDao.updateError(ASSET_METADATA_SYNC_STATE_ID, message) }
            _metadataSyncStatus.value = AssetMetadataSyncStatus(errorMessage = userFacingSyncError(error))
            Result.failure(error)
        }
    }

    override suspend fun clearRemoteReplica() {
        syncMutex.withLock {
            database.withTransaction {
                remoteAssetDao.clearAllRemoteAssets()
                syncStateDao.clearAll()
            }
            _metadataSyncStatus.value = AssetMetadataSyncStatus()
        }
    }

    override suspend fun refreshSignedUrl(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit> {
        val refreshKey = "$assetId:${variant.apiValue}"
        val pending = CompletableDeferred<Result<Unit>>()
        val active = signedUrlRefreshes.putIfAbsent(refreshKey, pending)
        if (active != null) return active.await()

        return try {
            val result = refreshSignedUrlOnce(assetId, variant, failedUrl)
            pending.complete(result)
            result
        } catch (error: CancellationException) {
            pending.cancel(error)
            throw error
        } finally {
            signedUrlRefreshes.remove(refreshKey, pending)
        }
    }

    private suspend fun refreshSignedUrlOnce(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit> {
        return try {
            val asset = remoteAssetDao.getAsset(assetId)
                ?: return Result.failure(IllegalArgumentException("Remote asset not found"))
            if (asset.keyFor(variant) == null) {
                return Result.failure(IllegalArgumentException("Asset variant is unavailable"))
            }
            if (failedUrl != null && asset.urlFor(variant) != failedUrl) {
                return Result.success(Unit)
            }

            val response = api.getReadUrl(assetId, variant.apiValue)
            val updatedAt = nowMillis()
            when (variant) {
                SignedUrlVariant.THUMBNAIL -> remoteAssetDao.updateThumbnailUrl(
                    assetId,
                    response.url,
                    updatedAt,
                    expiresAt = null,
                )
                SignedUrlVariant.PREVIEW -> remoteAssetDao.updatePreviewUrl(
                    assetId,
                    response.url,
                    updatedAt,
                    expiresAt = null,
                )
                SignedUrlVariant.POSTER_FRAME -> remoteAssetDao.updatePosterFrameUrl(
                    assetId,
                    response.url,
                    updatedAt,
                    expiresAt = null,
                )
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun emptySyncState() = RemoteSyncStateEntity(
        id = ASSET_METADATA_SYNC_STATE_ID,
        lastChangeCursor = 0,
        lastSyncedAt = null,
        lastError = null,
        isInitialSyncCompleted = false,
    )

    private fun userFacingSyncError(error: Throwable): String = when (error) {
        is HttpException -> when {
            error.code() == HTTP_UNAUTHORIZED -> "Session expired"
            error.code() >= HTTP_SERVER_ERROR -> "Server unavailable"
            else -> "Cannot sync cloud gallery"
        }
        is IOException -> "Network unavailable"
        else -> "Cannot sync cloud gallery"
    }

    private companion object {
        const val ASSET_CHANGES_PAGE_SIZE = 500
        const val MAX_BACKGROUND_SYNC_PAGES = 3
        const val MAX_FORCE_SYNC_PAGES = 10
        const val CHANGE_UPSERT = "upsert"
        const val CHANGE_TRASH = "trash"
        const val CHANGE_RESTORE = "restore"
        const val CHANGE_DELETE = "delete"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_SERVER_ERROR = 500
    }
}

private fun RemoteAssetEntity.urlFor(variant: SignedUrlVariant): String? = when (variant) {
    SignedUrlVariant.THUMBNAIL -> thumbnailUrl
    SignedUrlVariant.PREVIEW -> previewUrl
    SignedUrlVariant.POSTER_FRAME -> posterFrameUrl
}

private fun RemoteAssetEntity.keyFor(variant: SignedUrlVariant): String? = when (variant) {
    SignedUrlVariant.THUMBNAIL -> thumbnailKey
    SignedUrlVariant.PREVIEW -> previewKey
    SignedUrlVariant.POSTER_FRAME -> posterFrameKey
}
