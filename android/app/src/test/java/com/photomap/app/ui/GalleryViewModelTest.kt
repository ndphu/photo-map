package com.photomap.app.ui

import androidx.paging.PagingData
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.GalleryFilter
import com.photomap.app.data.gallery.GalleryBatchAction
import com.photomap.app.data.gallery.GalleryBatchActions
import com.photomap.app.data.gallery.GalleryBatchResult
import com.photomap.app.data.gallery.GalleryMediaType
import com.photomap.app.data.gallery.GalleryPager
import com.photomap.app.data.gallery.GalleryQuickFilter
import com.photomap.app.data.network.NetworkMonitor
import com.photomap.app.data.network.NetworkState
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.repository.GallerySyncController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
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
    fun filterUpdateRebuildsPager() = runTest(dispatcher) {
        val pager = RecordingGalleryPager()
        val viewModel = galleryViewModel(pager = pager)
        backgroundScope.launch { viewModel.pagingData.collect() }
        advanceUntilIdle()

        viewModel.updateFilter(GalleryFilter(mediaType = GalleryMediaType.VIDEO))
        advanceUntilIdle()

        assertEquals(listOf(GalleryMediaType.ALL, GalleryMediaType.VIDEO), pager.filters.map { it.mediaType })
    }

    @Test
    fun quickFiltersMapToBackendFilters() = runTest(dispatcher) {
        val viewModel = galleryViewModel()

        viewModel.selectQuickFilter(GalleryQuickFilter.PHOTOS)
        assertEquals(GalleryMediaType.IMAGE, viewModel.currentFilter.value.mediaType)
        assertEquals(false, viewModel.currentFilter.value.favoriteOnly)

        viewModel.selectQuickFilter(GalleryQuickFilter.VIDEOS)
        assertEquals(GalleryMediaType.VIDEO, viewModel.currentFilter.value.mediaType)

        viewModel.selectQuickFilter(GalleryQuickFilter.FAVORITES)
        assertEquals(GalleryMediaType.ALL, viewModel.currentFilter.value.mediaType)
        assertEquals(true, viewModel.currentFilter.value.favoriteOnly)

        viewModel.selectQuickFilter(GalleryQuickFilter.ALL)
        assertEquals(GalleryFilter(), viewModel.currentFilter.value)
    }

    @Test
    fun networkStateUpdatesUiState() = runTest(dispatcher) {
        val networkMonitor = FakeNetworkMonitor()
        val viewModel = galleryViewModel(networkMonitor = networkMonitor)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        networkMonitor.mutableState.value = NetworkState.OFFLINE
        advanceUntilIdle()

        assertEquals(NetworkState.OFFLINE, viewModel.uiState.value.networkState)
    }

    @Test
    fun refreshEmitsPagingRefreshCommand() = runTest(dispatcher) {
        val viewModel = galleryViewModel()
        val commands = mutableListOf<GalleryCommand>()
        backgroundScope.launch { viewModel.commands.collect { commands += it } }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(GalleryCommand.REFRESH), commands)
    }

    @Test
    fun selectionEntersTogglesAndClears() = runTest(dispatcher) {
        val viewModel = galleryViewModel()

        viewModel.selectAsset("one")
        assertEquals(setOf("one"), viewModel.interactionState.value.selectedIds)

        assertEquals(false, viewModel.onAssetTap("two"))
        assertEquals(setOf("one", "two"), viewModel.interactionState.value.selectedIds)
        assertEquals(false, viewModel.onAssetTap("one"))
        assertEquals(setOf("two"), viewModel.interactionState.value.selectedIds)

        viewModel.clearSelection()
        assertTrue(viewModel.interactionState.value.selectedIds.isEmpty())
        assertEquals(true, viewModel.onAssetTap("three"))
    }

    @Test
    fun filterChangeClearsSelection() = runTest(dispatcher) {
        val viewModel = galleryViewModel()
        viewModel.selectAsset("one")

        viewModel.selectQuickFilter(GalleryQuickFilter.VIDEOS)

        assertTrue(viewModel.interactionState.value.selectedIds.isEmpty())
    }

    @Test
    fun batchPartialFailureKeepsFailedItemsSelectedAndRetries() = runTest(dispatcher) {
        val batch = FakeGalleryBatchActions(failedIds = setOf("two"))
        val viewModel = galleryViewModel(batchActions = batch)
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectAsset("one")
        viewModel.selectAsset("two")

        viewModel.archiveSelected()
        advanceUntilIdle()

        assertEquals(setOf("two"), viewModel.interactionState.value.selectedIds)
        assertEquals("1 succeeded, 1 failed", viewModel.interactionState.value.resultMessage)
        assertEquals(true, viewModel.interactionState.value.canRetryBatch)

        batch.failedIds = emptySet()
        viewModel.retryFailedBatch()
        advanceUntilIdle()
        assertTrue(viewModel.interactionState.value.selectedIds.isEmpty())
        assertEquals(2, batch.actions.size)
    }

    @Test
    fun addSelectedToAlbumUsesBatchActionAndClearsSelection() = runTest(dispatcher) {
        val batch = FakeGalleryBatchActions()
        val viewModel = galleryViewModel(batchActions = batch)
        viewModel.selectAsset("one")
        viewModel.selectAsset("two")

        viewModel.addSelectedToAlbum("album-id")
        advanceUntilIdle()

        assertEquals(listOf(GalleryBatchAction.AddToAlbum("album-id")), batch.actions)
        assertTrue(viewModel.interactionState.value.selectedIds.isEmpty())
    }

    @Test
    fun syncActionsUseExistingController() = runTest(dispatcher) {
        val sync = FakeGallerySyncController()
        val viewModel = galleryViewModel(syncController = sync)

        viewModel.startSync()
        viewModel.retryFailedUploads()
        advanceUntilIdle()

        assertEquals(1, sync.scanCalls)
        assertEquals(1, sync.retryCalls)
    }

    @Test
    fun syncCountsAreExposedAndUploadCompletionRefreshesGallery() = runTest(dispatcher) {
        val sync = FakeGallerySyncController()
        val viewModel = galleryViewModel(syncController = sync)
        val commands = mutableListOf<GalleryCommand>()
        backgroundScope.launch { viewModel.uiState.collect() }
        backgroundScope.launch { viewModel.commands.collect { commands += it } }
        advanceUntilIdle()

        sync.pendingCount.value = 3
        sync.uploadingCount.value = 2
        sync.failedCount.value = 1
        sync.uploadedCount.value = 4
        advanceUntilIdle()

        assertEquals(GallerySyncSummary(pending = 3, uploading = 2, failed = 1, uploaded = 4), viewModel.uiState.value.sync)
        assertEquals(listOf(GalleryCommand.REFRESH), commands)
    }

    private fun galleryViewModel(
        pager: GalleryPager = RecordingGalleryPager(),
        networkMonitor: NetworkMonitor = FakeNetworkMonitor(),
        batchActions: GalleryBatchActions = FakeGalleryBatchActions(),
        syncController: GallerySyncController = FakeGallerySyncController(),
    ) = GalleryViewModel(pager, networkMonitor, batchActions, syncController)
}

private class RecordingGalleryPager : GalleryPager {
    val filters = mutableListOf<GalleryFilter>()
    override val invalidationVersion: StateFlow<Long> = MutableStateFlow(0L)

    override fun getGalleryPager(filter: GalleryFilter): Flow<PagingData<AssetUiModel>> {
        filters += filter
        return flowOf(PagingData.empty())
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val mutableState = MutableStateFlow(NetworkState.ONLINE)
    override val state: Flow<NetworkState> = mutableState
}

private class FakeGalleryBatchActions(
    var failedIds: Set<String> = emptySet(),
) : GalleryBatchActions {
    val actions = mutableListOf<GalleryBatchAction>()

    override suspend fun listAlbums(): List<AlbumDto> = emptyList()

    override suspend fun execute(
        assetIds: Set<String>,
        action: GalleryBatchAction,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): GalleryBatchResult {
        actions += action
        onProgress(assetIds.size, assetIds.size)
        return GalleryBatchResult(
            total = assetIds.size,
            succeeded = assetIds.size - failedIds.size,
            failedIds = failedIds,
        )
    }
}

private class FakeGallerySyncController : GallerySyncController {
    override val pendingCount = MutableStateFlow(0)
    override val failedCount = MutableStateFlow(0)
    override val uploadingCount = MutableStateFlow(0)
    override val uploadedCount = MutableStateFlow(0)
    var scanCalls = 0
    var retryCalls = 0

    override suspend fun scanAndSync() {
        scanCalls += 1
    }

    override suspend fun retryFailed() {
        retryCalls += 1
    }
}
