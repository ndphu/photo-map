package com.photomap.app.ui

import com.photomap.app.data.gallery.GalleryInvalidator
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.AssetDetailModel
import com.photomap.app.data.gallery.AssetDetailStore
import com.photomap.app.data.gallery.SignedUrlVariant
import com.photomap.app.data.gallery.ViewerAssetSummary
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.repository.AssetRemoteDataSource
import com.photomap.app.data.repository.AssetRepository
import com.photomap.app.data.repository.OriginalImageStore
import com.photomap.app.data.repository.OriginalTransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.File
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun favoriteSuccessUpdatesDetail() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.asset?.isFavorite == true)
    }

    @Test
    fun favoriteFailureKeepsServerState() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply { favoriteError = IOException("offline") }
        val viewModel = viewModel(remote)
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.asset?.isFavorite == true)
        assertEquals("Network unavailable", viewModel.state.value.error)
    }

    @Test
    fun archiveSuccessNavigatesBack() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.toggleArchive()
        advanceUntilIdle()

        assertTrue(remote.asset.isArchived)
        assertEquals(listOf(AssetDetailEvent.NAVIGATE_BACK), events)
    }

    @Test
    fun trashSuccessNavigatesBack() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.trash()
        advanceUntilIdle()

        assertTrue(remote.asset.isTrashed)
        assertEquals(listOf(AssetDetailEvent.NAVIGATE_BACK), events)
    }

    @Test
    fun restoreSuccessNavigatesBack() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply { asset = asset.copy(isTrashed = true) }
        val viewModel = viewModel(remote)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.restore()
        advanceUntilIdle()

        assertFalse(remote.asset.isTrashed)
        assertEquals(listOf(AssetDetailEvent.NAVIGATE_BACK), events)
    }

    @Test
    fun hardDeleteRequiresConfirmation() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.requestHardDelete()
        assertTrue(viewModel.state.value.showDeleteConfirmation)
        assertEquals(0, remote.deleteCalls)

        viewModel.confirmHardDelete()
        advanceUntilIdle()

        assertEquals(1, remote.deleteCalls)
        assertEquals(listOf(AssetDetailEvent.NAVIGATE_BACK), events)
    }

    @Test
    fun imageAssetLoadsPreviewReadUrl() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)

        advanceUntilIdle()

        assertEquals(listOf("preview"), remote.readVariants)
        assertEquals("signed-url-1", viewModel.state.value.previewUrl)
    }

    @Test
    fun retryRequestsFreshReadUrl() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        advanceUntilIdle()

        viewModel.onPreviewLoadFailed()
        viewModel.refreshReadUrl()
        advanceUntilIdle()

        assertEquals(listOf("preview", "preview"), remote.readVariants)
        assertEquals("signed-url-2", viewModel.state.value.previewUrl)
        assertFalse(viewModel.state.value.previewLoadFailed)
    }

    @Test
    fun readUrlFailureExposesError() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply { readUrlError = IOException("offline") }
        val viewModel = viewModel(remote)

        advanceUntilIdle()

        assertEquals("Network unavailable", viewModel.state.value.error)
        assertTrue(viewModel.state.value.previewLoadFailed)
    }

    @Test
    fun unauthorizedReadUrlUsesSessionExpiredState() = runTest(dispatcher) {
        val unauthorized = HttpException(Response.error<Any>(401, "unauthorized".toResponseBody()))
        val remote = FakeAssetRemoteDataSource().apply { readUrlError = unauthorized }
        val viewModel = viewModel(remote)

        advanceUntilIdle()

        assertEquals("Session expired", viewModel.state.value.error)
    }

    @Test
    fun assetIdChangeReloadsDetailAndPreviewUrl() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        advanceUntilIdle()

        viewModel.loadAsset("next-asset")
        advanceUntilIdle()

        assertEquals(listOf(ASSET_ID, "next-asset"), remote.detailIds)
        assertEquals("next-asset", viewModel.state.value.asset?.id)
        assertEquals("signed-url-2", viewModel.state.value.previewUrl)
    }

    @Test
    fun detailsPanelOpensAndClosesWithoutRefetching() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val viewModel = viewModel(remote)
        advanceUntilIdle()

        viewModel.openDetails()
        assertTrue(viewModel.state.value.showDetails)
        viewModel.closeDetails()
        assertFalse(viewModel.state.value.showDetails)

        assertEquals(listOf(ASSET_ID), remote.detailIds)
    }

    @Test
    fun retryReloadsDetailAfterFailure() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply { detailError = IOException("offline") }
        val viewModel = viewModel(remote)
        advanceUntilIdle()
        assertEquals("Network unavailable", viewModel.state.value.error)

        remote.detailError = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, remote.detailIds.size)
        assertEquals(ASSET_ID, viewModel.state.value.asset?.id)
    }

    @Test
    fun viewerSequenceExposesAdjacentAssetsAndLoadsSelectedAsset() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val store = FakeAssetDetailStore(
            assets = listOf(viewerAsset(ASSET_ID), viewerAsset("next-asset")),
        )
        val viewModel = viewModel(remote, store)
        advanceUntilIdle()

        assertEquals(listOf(ASSET_ID, "next-asset"), viewModel.state.value.viewerAssets.map { it.id })

        viewModel.loadAsset("next-asset")
        advanceUntilIdle()

        assertEquals("next-asset", viewModel.state.value.activeAssetId)
        assertEquals("next-asset", viewModel.state.value.asset?.id)
    }

    @Test
    fun localAssetRendersWhenDetailRequestFails() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply { detailError = IOException("offline") }
        val local = localAsset(ASSET_ID, previewUrl = "cached-preview")
        val viewModel = viewModel(
            remote,
            FakeAssetDetailStore(listOf(viewerAsset(ASSET_ID, "cached-preview")), mapOf(ASSET_ID to local)),
        )

        advanceUntilIdle()

        assertEquals(ASSET_ID, viewModel.state.value.asset?.id)
        assertEquals("cached-preview", viewModel.state.value.previewUrl)
        assertFalse(viewModel.state.value.loading)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun localPreviewDoesNotRequestReadUrl() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource()
        val local = localAsset(ASSET_ID, previewUrl = "cached-preview")
        val viewModel = viewModel(
            remote,
            FakeAssetDetailStore(listOf(viewerAsset(ASSET_ID, "cached-preview")), mapOf(ASSET_ID to local)),
        )

        advanceUntilIdle()

        assertTrue(remote.readVariants.isEmpty())
        assertEquals("cached-preview", viewModel.state.value.previewUrl)
    }

    @Test
    fun remoteDetailDoesNotOverwriteLocalFavoriteState() = runTest(dispatcher) {
        val remote = FakeAssetRemoteDataSource().apply {
            asset = asset.copy(isFavorite = false, software = "Android")
        }
        val local = localAsset(ASSET_ID, previewUrl = "cached-preview").copy(isFavorite = true)
        val viewModel = viewModel(
            remote,
            FakeAssetDetailStore(listOf(viewerAsset(ASSET_ID, "cached-preview")), mapOf(ASSET_ID to local)),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.asset?.isFavorite == true)
        assertEquals("Android", viewModel.state.value.asset?.software)
    }

    @Test
    fun navigationSeedProvidesPreviewBeforeRoomEmits() = runTest(dispatcher) {
        val seed = viewerAsset(ASSET_ID, previewUrl = "navigation-preview")
        val store = FakeAssetDetailStore(assets = emptyList())

        val viewModel = AssetDetailViewModel(
            ASSET_ID,
            AssetRepository(FakeAssetRemoteDataSource(), GalleryInvalidator()),
            EmptyAlbumStore,
            store,
            seed,
        )

        assertEquals("navigation-preview", viewModel.state.value.previewUrl)
        assertEquals(listOf(seed), viewModel.state.value.viewerAssets)
    }

    @Test
    fun loadOriginalKeepsPreviewUntilOriginalIsReady() = runTest(dispatcher) {
        val originalStore = FakeOriginalImageStore()
        val local = localAsset(ASSET_ID, previewUrl = "cached-preview")
        val viewModel = viewModel(
            FakeAssetRemoteDataSource(),
            FakeAssetDetailStore(listOf(viewerAsset(ASSET_ID, "cached-preview")), mapOf(ASSET_ID to local)),
            originalStore,
        )
        advanceUntilIdle()

        viewModel.loadOriginal()
        assertEquals("cached-preview", viewModel.state.value.previewUrl)
        advanceUntilIdle()

        assertEquals(OriginalImageStatus.READY, viewModel.state.value.originalStatus)
        assertEquals(originalStore.preparedFile.absolutePath, viewModel.state.value.originalFilePath)
    }

    @Test
    fun originalFailureLeavesPreviewVisible() = runTest(dispatcher) {
        val originalStore = FakeOriginalImageStore().apply { prepareError = IOException("offline") }
        val local = localAsset(ASSET_ID, previewUrl = "cached-preview")
        val viewModel = viewModel(
            FakeAssetRemoteDataSource(),
            FakeAssetDetailStore(listOf(viewerAsset(ASSET_ID, "cached-preview")), mapOf(ASSET_ID to local)),
            originalStore,
        )
        advanceUntilIdle()

        viewModel.loadOriginal()
        advanceUntilIdle()

        assertEquals(OriginalImageStatus.FAILED, viewModel.state.value.originalStatus)
        assertEquals("cached-preview", viewModel.state.value.previewUrl)
        assertEquals("Network unavailable", viewModel.state.value.originalMessage)
    }

    @Test
    fun swipingDeletesPreparedOriginal() = runTest(dispatcher) {
        val originalStore = FakeOriginalImageStore()
        val assets = listOf(viewerAsset(ASSET_ID, "cached-preview"), viewerAsset("next-asset", "next-preview"))
        val viewModel = viewModel(
            FakeAssetRemoteDataSource(),
            FakeAssetDetailStore(assets, mapOf(ASSET_ID to localAsset(ASSET_ID, "cached-preview"))),
            originalStore,
        )
        advanceUntilIdle()
        viewModel.loadOriginal()
        advanceUntilIdle()

        viewModel.loadAsset("next-asset")

        assertEquals(listOf(ASSET_ID), originalStore.deletedAssetIds)
        assertEquals(null, viewModel.state.value.originalFilePath)
    }

    private fun viewModel(
        remote: FakeAssetRemoteDataSource,
        store: AssetDetailStore? = null,
        originalStore: OriginalImageStore? = null,
    ) = if (store == null) {
        AssetDetailViewModel(
            ASSET_ID,
            AssetRepository(remote, GalleryInvalidator()),
            EmptyAlbumStore,
            originalImageStore = originalStore ?: FakeOriginalImageStore(),
        )
    } else {
        AssetDetailViewModel(
            ASSET_ID,
            AssetRepository(remote, GalleryInvalidator()),
            EmptyAlbumStore,
            store,
            originalImageStore = originalStore ?: FakeOriginalImageStore(),
        )
    }

    private fun kotlinx.coroutines.test.TestScope.collectEvents(
        viewModel: AssetDetailViewModel,
    ): MutableList<AssetDetailEvent> {
        val events = mutableListOf<AssetDetailEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }

    private companion object {
        const val ASSET_ID = "asset-id"
    }
}

private fun viewerAsset(id: String, previewUrl: String? = null) = ViewerAssetSummary(
    id = id,
    mediaType = "image",
    originalFilename = "$id.jpg",
    thumbnailUrl = null,
    previewUrl = previewUrl,
)

private fun localAsset(id: String, previewUrl: String?) = AssetDetailModel(
    id = id,
    mediaType = "image",
    mimeType = "image/jpeg",
    originalFilename = "$id.jpg",
    previewKey = "previews/$id.webp",
    previewUrl = previewUrl,
)

private class FakeAssetDetailStore(
    private val assets: List<ViewerAssetSummary>,
    private val localAssets: Map<String, AssetDetailModel> = emptyMap(),
) : AssetDetailStore {
    override fun observeViewerAssets() = flowOf(assets)

    override fun observeAssetDetail(assetId: String) = flowOf(localAssets[assetId])

    override suspend fun refreshSignedUrl(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit> = Result.success(Unit)
}

private class FakeOriginalImageStore : OriginalImageStore {
    val preparedFile: File = File("build/test-original.jpg")
    val deletedAssetIds = mutableListOf<String>()
    var prepareError: Exception? = null

    override suspend fun prepareOriginal(
        assetId: String,
        originalFilename: String?,
        onProgress: (OriginalTransferProgress) -> Unit,
    ): File {
        prepareError?.let { throw it }
        onProgress(OriginalTransferProgress(100L, 100L))
        return preparedFile
    }

    override suspend fun saveOriginal(
        assetId: String,
        destination: android.net.Uri,
        onProgress: (OriginalTransferProgress) -> Unit,
    ) = Unit

    override fun deletePreparedOriginal(assetId: String) {
        deletedAssetIds += assetId
    }

    override fun clearPreparedOriginals() = Unit
}

private object EmptyAlbumStore : AlbumStore {
    override suspend fun listAlbums(): List<AlbumDto> = emptyList()
    override suspend fun getAlbum(id: String): AlbumDto = error("unused")
    override suspend fun createAlbum(name: String, description: String?): AlbumDto = error("unused")
    override suspend fun updateAlbum(id: String, name: String, description: String?): AlbumDto = error("unused")
    override suspend fun deleteAlbum(id: String) = Unit
    override suspend fun listAssets(albumId: String): List<AssetUiModel> = emptyList()
    override suspend fun addAsset(albumId: String, assetId: String) = Unit
    override suspend fun removeAsset(albumId: String, assetId: String) = Unit
}

private class FakeAssetRemoteDataSource : AssetRemoteDataSource {
    var asset = testAsset()
    var favoriteError: Exception? = null
    var detailError: Exception? = null
    var readUrlError: Exception? = null
    var deleteCalls = 0
    val detailIds = mutableListOf<String>()
    val readVariants = mutableListOf<String>()
    private var readUrlVersion = 0

    override suspend fun detail(id: String): AssetDetailDto {
        detailIds += id
        detailError?.let { throw it }
        asset = asset.copy(id = id)
        return asset
    }

    override suspend fun readUrl(id: String, variant: String): String {
        readVariants += variant
        readUrlError?.let { throw it }
        readUrlVersion += 1
        return "signed-url-$readUrlVersion"
    }

    override suspend fun favorite(id: String, favorite: Boolean): AssetDetailDto {
        favoriteError?.let { throw it }
        asset = asset.copy(isFavorite = favorite)
        return asset
    }

    override suspend fun archive(id: String, archived: Boolean): AssetDetailDto {
        asset = asset.copy(isArchived = archived)
        return asset
    }

    override suspend fun trash(id: String): AssetDetailDto {
        asset = asset.copy(isTrashed = true)
        return asset
    }

    override suspend fun restore(id: String): AssetDetailDto {
        asset = asset.copy(isTrashed = false)
        return asset
    }

    override suspend fun delete(id: String) {
        deleteCalls += 1
    }
}

private fun testAsset() = AssetDetailDto(
    id = "asset-id",
    mediaType = "image",
    mimeType = "image/jpeg",
    objectKey = "users/user/original.jpg",
    thumbnailKey = null,
    previewKey = null,
    posterFrameKey = null,
    originalFilename = "photo.jpg",
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
