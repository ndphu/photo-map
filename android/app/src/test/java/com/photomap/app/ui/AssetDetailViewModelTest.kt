package com.photomap.app.ui

import com.photomap.app.data.gallery.GalleryInvalidator
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.repository.AssetRemoteDataSource
import com.photomap.app.data.repository.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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

    private fun viewModel(remote: FakeAssetRemoteDataSource) = AssetDetailViewModel(
        ASSET_ID,
        AssetRepository(remote, GalleryInvalidator()),
        EmptyAlbumStore,
    )

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
    var readUrlError: Exception? = null
    var deleteCalls = 0
    val detailIds = mutableListOf<String>()
    val readVariants = mutableListOf<String>()
    private var readUrlVersion = 0

    override suspend fun detail(id: String): AssetDetailDto {
        detailIds += id
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
