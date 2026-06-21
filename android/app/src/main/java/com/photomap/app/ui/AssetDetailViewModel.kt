package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.photomap.app.data.gallery.AssetDetailModel
import com.photomap.app.data.gallery.AssetDetailStore
import com.photomap.app.data.gallery.SignedUrlVariant
import com.photomap.app.data.gallery.ViewerAssetSummary
import com.photomap.app.data.gallery.toDetailModel
import com.photomap.app.data.gallery.withLocalReplica
import com.photomap.app.data.gallery.withRemoteDetails
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.repository.AssetRepository
import com.photomap.app.data.repository.OriginalDownloadException
import com.photomap.app.data.repository.OriginalImageStore
import com.photomap.app.data.repository.OriginalTransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class AssetDetailUiState(
    val activeAssetId: String? = null,
    val asset: AssetDetailModel? = null,
    val previewUrl: String? = null,
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val error: String? = null,
    val previewLoadFailed: Boolean = false,
    val isRefreshingUrl: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showAlbumPicker: Boolean = false,
    val showDetails: Boolean = false,
    val albums: List<AlbumDto> = emptyList(),
    val viewerAssets: List<ViewerAssetSummary> = emptyList(),
    val originalStatus: OriginalImageStatus = OriginalImageStatus.IDLE,
    val originalFilePath: String? = null,
    val originalTransferredBytes: Long = 0L,
    val originalTotalBytes: Long? = null,
    val originalMessage: String? = null,
)

enum class OriginalImageStatus {
    IDLE,
    LOADING,
    READY,
    FAILED,
    DOWNLOADING,
}

enum class AssetDetailEvent {
    NAVIGATE_BACK,
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailViewModel(
    assetId: String,
    private val repository: AssetRepository,
    private val albumRepository: AlbumStore,
    private val detailStore: AssetDetailStore = singleAssetDetailStore(assetId),
    initialAsset: ViewerAssetSummary? = null,
    private val originalImageStore: OriginalImageStore = UnavailableOriginalImageStore,
) : ViewModel() {
    private var activeAssetId = assetId
    private val activeAssetIds = MutableStateFlow(assetId)
    private var remoteLoadJob: Job? = null
    private var originalTransferJob: Job? = null
    private var localAssetAvailable = false
    private val _state = MutableStateFlow(initialDetailState(assetId, initialAsset))
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AssetDetailEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        detailStore.observeViewerAssets()
            .onEach(::updateViewerAssets)
            .launchIn(viewModelScope)
        activeAssetIds
            .flatMapLatest(detailStore::observeAssetDetail)
            .onEach(::updateLocalAsset)
            .launchIn(viewModelScope)
        loadRemoteDetail()
    }

    fun toggleFavorite() {
        val asset = _state.value.asset ?: return
        val favorite = !asset.isFavorite
        mutate(
            fallbackError = "Cannot update asset",
            optimisticUpdate = { it.copy(isFavorite = favorite) },
        ) { repository.setFavorite(activeAssetId, favorite) }
    }

    fun toggleArchive() {
        val asset = _state.value.asset ?: return
        val archived = !asset.isArchived
        mutate(
            fallbackError = "Cannot update asset",
            navigateBack = true,
            optimisticUpdate = { it.copy(isArchived = archived) },
        ) { repository.setArchived(activeAssetId, archived) }
    }

    fun trash() {
        mutate(
            fallbackError = "Cannot update asset",
            navigateBack = true,
            optimisticUpdate = { it.copy(isTrashed = true) },
        ) { repository.trash(activeAssetId) }
    }

    fun restore() {
        mutate(
            fallbackError = "Cannot update asset",
            navigateBack = true,
            optimisticUpdate = { it.copy(isTrashed = false) },
        ) { repository.restore(activeAssetId) }
    }

    fun requestHardDelete() {
        if (_state.value.asset != null) {
            _state.value = _state.value.copy(showDeleteConfirmation = true, error = null)
        }
    }

    fun showAlbumPicker() {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, error = null)
            try {
                _state.value = _state.value.copy(
                    albums = albumRepository.listAlbums(),
                    showAlbumPicker = true,
                    actionInProgress = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    error = "Cannot load albums",
                )
            }
        }
    }

    fun dismissAlbumPicker() {
        _state.value = _state.value.copy(showAlbumPicker = false)
    }

    fun addToAlbum(albumId: String) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, error = null)
            try {
                albumRepository.addAsset(albumId, activeAssetId)
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    showAlbumPicker = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    error = "Cannot add asset to album",
                )
            }
        }
    }

    fun cancelHardDelete() {
        _state.value = _state.value.copy(showDeleteConfirmation = false)
    }

    fun confirmHardDelete() {
        if (_state.value.asset == null || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                actionInProgress = true,
                showDeleteConfirmation = false,
                error = null,
            )
            try {
                repository.delete(activeAssetId)
                _events.emit(AssetDetailEvent.NAVIGATE_BACK)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    error = userFacingError(error, "Cannot delete asset"),
                )
            }
        }
    }

    fun retry() {
        if (_state.value.loading || _state.value.actionInProgress) return
        load()
    }

    fun retryPreview() = refreshReadUrl()

    fun openDetails() {
        if (_state.value.asset != null) {
            _state.value = _state.value.copy(showDetails = true)
        }
    }

    fun closeDetails() {
        _state.value = _state.value.copy(showDetails = false)
    }

    fun refreshReadUrl() {
        if (_state.value.actionInProgress || _state.value.isRefreshingUrl || _state.value.asset == null) return
        viewModelScope.launch { refreshPreviewUrl(activeAssetId, _state.value.previewUrl) }
    }

    fun loadOriginal() {
        val asset = _state.value.asset ?: return
        if (asset.mediaType != "image" || originalTransferJob?.isActive == true) return
        originalTransferJob = viewModelScope.launch {
            val requestedAssetId = activeAssetId
            _state.value = _state.value.copy(
                originalStatus = OriginalImageStatus.LOADING,
                originalFilePath = null,
                originalTransferredBytes = 0L,
                originalTotalBytes = null,
                originalMessage = null,
            )
            try {
                val file = originalImageStore.prepareOriginal(
                    requestedAssetId,
                    asset.originalFilename,
                ) { progress ->
                    if (requestedAssetId == activeAssetId) updateOriginalProgress(progress)
                }
                if (requestedAssetId != activeAssetId) {
                    originalImageStore.deletePreparedOriginal(requestedAssetId)
                    return@launch
                }
                _state.value = _state.value.copy(
                    originalStatus = OriginalImageStatus.READY,
                    originalFilePath = file.absolutePath,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestedAssetId != activeAssetId) return@launch
                _state.value = _state.value.copy(
                    originalStatus = OriginalImageStatus.FAILED,
                    originalFilePath = null,
                    originalMessage = originalTransferError(error, "Cannot load original"),
                )
            }
        }
    }

    fun usePreview() {
        val assetId = activeAssetId
        originalTransferJob?.cancel()
        originalImageStore.deletePreparedOriginal(assetId)
        _state.value = _state.value.copy(
            originalStatus = OriginalImageStatus.IDLE,
            originalFilePath = null,
            originalTransferredBytes = 0L,
            originalTotalBytes = null,
        )
    }

    fun downloadOriginal(destination: Uri) {
        val asset = _state.value.asset ?: return
        if (asset.mediaType != "image" || originalTransferJob?.isActive == true) return
        val returnStatus = if (_state.value.originalFilePath != null) {
            OriginalImageStatus.READY
        } else {
            OriginalImageStatus.IDLE
        }
        originalTransferJob = viewModelScope.launch {
            val requestedAssetId = activeAssetId
            _state.value = _state.value.copy(
                originalStatus = OriginalImageStatus.DOWNLOADING,
                originalTransferredBytes = 0L,
                originalTotalBytes = null,
                originalMessage = null,
            )
            try {
                originalImageStore.saveOriginal(requestedAssetId, destination) { progress ->
                    if (requestedAssetId == activeAssetId) updateOriginalProgress(progress)
                }
                if (requestedAssetId != activeAssetId) return@launch
                _state.value = _state.value.copy(
                    originalStatus = returnStatus,
                    originalMessage = "Original saved",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestedAssetId != activeAssetId) return@launch
                _state.value = _state.value.copy(
                    originalStatus = returnStatus,
                    originalMessage = originalTransferError(error, "Cannot download original"),
                )
            }
        }
    }

    fun clearOriginalMessage() {
        _state.value = _state.value.copy(originalMessage = null)
    }

    fun loadAsset(assetId: String) {
        if (assetId == activeAssetId) return
        cancelOriginalTransfer(activeAssetId)
        activeAssetId = assetId
        localAssetAvailable = false
        val viewerAsset = _state.value.viewerAssets.firstOrNull { it.id == assetId }
        _state.value = AssetDetailUiState(
            activeAssetId = assetId,
            previewUrl = viewerAsset?.previewUrl,
            viewerAssets = _state.value.viewerAssets,
        )
        activeAssetIds.value = assetId
        loadRemoteDetail()
    }

    private fun updateViewerAssets(assets: List<ViewerAssetSummary>) {
        val normalized = if (assets.any { it.id == activeAssetId }) {
            assets
        } else {
            listOf(currentViewerAsset())
        }
        val activeAsset = normalized.firstOrNull { it.id == activeAssetId }
        _state.value = _state.value.copy(
            viewerAssets = normalized,
            previewUrl = activeAsset?.previewUrl ?: _state.value.previewUrl,
        )
    }

    private fun currentViewerAsset(): ViewerAssetSummary {
        val asset = _state.value.asset
        return ViewerAssetSummary(
            id = activeAssetId,
            mediaType = asset?.mediaType,
            originalFilename = asset?.originalFilename,
            thumbnailUrl = null,
            previewUrl = _state.value.previewUrl,
        )
    }

    private fun updateLocalAsset(localAsset: AssetDetailModel?) {
        if (localAsset?.id != activeAssetId) return
        localAssetAvailable = true
        val current = _state.value.asset?.takeIf { it.id == localAsset.id }
        _state.value = _state.value.copy(
            asset = current?.withLocalReplica(localAsset) ?: localAsset,
            previewUrl = localAsset.previewUrl ?: currentPreviewUrl(localAsset.id),
            loading = false,
            error = null,
        )
    }

    private fun currentPreviewUrl(assetId: String): String? =
        _state.value.viewerAssets.firstOrNull { it.id == assetId }?.previewUrl
            ?: _state.value.previewUrl

    private fun updateOriginalProgress(progress: OriginalTransferProgress) {
        _state.value = _state.value.copy(
            originalTransferredBytes = progress.transferredBytes,
            originalTotalBytes = progress.totalBytes,
        )
    }

    private fun cancelOriginalTransfer(assetId: String) {
        originalTransferJob?.cancel()
        originalTransferJob = null
        originalImageStore.deletePreparedOriginal(assetId)
    }

    fun onPreviewLoadFailed() {
        _state.value = _state.value.copy(previewLoadFailed = true)
        refreshReadUrl()
    }

    fun onPreviewLoaded() {
        _state.value = _state.value.copy(previewLoadFailed = false)
    }

    private fun load() = loadRemoteDetail()

    private fun loadRemoteDetail() {
        remoteLoadJob?.cancel()
        remoteLoadJob = viewModelScope.launch {
            val requestedAssetId = activeAssetId
            val hasLocalAsset = _state.value.asset?.id == requestedAssetId
            _state.value = _state.value.copy(loading = !hasLocalAsset, error = null)
            try {
                val remoteAsset = repository.detail(requestedAssetId)
                if (requestedAssetId != activeAssetId) return@launch
                val current = _state.value.asset?.takeIf { it.id == requestedAssetId }
                _state.value = _state.value.copy(
                    asset = current?.withRemoteDetails(remoteAsset) ?: remoteAsset.toDetailModel(),
                    loading = false,
                )
                if (_state.value.previewUrl == null) {
                    refreshPreviewUrl(requestedAssetId, failedUrl = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestedAssetId != activeAssetId) return@launch
                val localAvailable = _state.value.asset?.id == requestedAssetId
                _state.value = _state.value.copy(
                    loading = false,
                    error = userFacingError(error, "Server unavailable").takeUnless { localAvailable },
                )
            }
        }
    }

    private suspend fun refreshPreviewUrl(requestedAssetId: String, failedUrl: String?) {
        _state.value = _state.value.copy(isRefreshingUrl = true, error = null)
        try {
            if (localAssetAvailable) {
                detailStore.refreshSignedUrl(
                    requestedAssetId,
                    SignedUrlVariant.PREVIEW,
                    failedUrl,
                ).getOrThrow()
            } else {
                val url = repository.previewUrl(requestedAssetId)
                if (requestedAssetId != activeAssetId) return
                _state.value = _state.value.copy(previewUrl = url)
            }
            if (requestedAssetId != activeAssetId) return
            _state.value = _state.value.copy(
                previewLoadFailed = false,
                isRefreshingUrl = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requestedAssetId != activeAssetId) return
            _state.value = _state.value.copy(
                previewLoadFailed = true,
                isRefreshingUrl = false,
                error = userFacingError(error, "Cannot load photo"),
            )
        }
    }

    private fun mutate(
        fallbackError: String,
        navigateBack: Boolean = false,
        optimisticUpdate: (AssetDetailModel) -> AssetDetailModel,
        action: suspend () -> Unit,
    ) {
        if (_state.value.asset == null || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, error = null)
            try {
                action()
                val currentAsset = _state.value.asset
                _state.value = _state.value.copy(
                    asset = currentAsset?.let(optimisticUpdate),
                    actionInProgress = false,
                )
                if (navigateBack) _events.emit(AssetDetailEvent.NAVIGATE_BACK)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionInProgress = false,
                    error = userFacingError(error, fallbackError),
                )
            }
        }
    }

    override fun onCleared() {
        originalTransferJob?.cancel()
        originalImageStore.clearPreparedOriginals()
        super.onCleared()
    }
}

private fun userFacingError(error: Exception, fallback: String): String = when {
    error is HttpException && error.code() == 401 -> "Session expired"
    error is IOException -> "Network unavailable"
    error is HttpException && error.code() >= 500 -> "Server unavailable"
    else -> fallback
}

private fun originalTransferError(error: Exception, fallback: String): String = when {
    error is HttpException && error.code() == 401 -> "Session expired"
    error is OriginalDownloadException -> fallback
    error is IOException -> "Network unavailable"
    else -> fallback
}

class AssetDetailViewModelFactory(
    private val assetId: String,
    private val repository: AssetRepository,
    private val albumRepository: AlbumStore,
    private val detailStore: AssetDetailStore,
    private val initialAsset: ViewerAssetSummary?,
    private val originalImageStore: OriginalImageStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(
            assetId,
            repository,
            albumRepository,
            detailStore,
            initialAsset,
            originalImageStore,
        ) as T
}

private fun initialDetailState(assetId: String, initialAsset: ViewerAssetSummary?) = AssetDetailUiState(
    activeAssetId = assetId,
    previewUrl = initialAsset?.previewUrl,
    viewerAssets = initialAsset?.let(::listOf).orEmpty(),
)

private fun singleAssetDetailStore(assetId: String): AssetDetailStore = object : AssetDetailStore {
    override fun observeViewerAssets() = flowOf(
        listOf(ViewerAssetSummary(assetId, null, null, null, null)),
    )

    override fun observeAssetDetail(assetId: String) = flowOf<AssetDetailModel?>(null)

    override suspend fun refreshSignedUrl(
        assetId: String,
        variant: SignedUrlVariant,
        failedUrl: String?,
    ): Result<Unit> = Result.failure(IllegalStateException("Local asset is unavailable"))
}

private object UnavailableOriginalImageStore : OriginalImageStore {
    override suspend fun prepareOriginal(
        assetId: String,
        originalFilename: String?,
        onProgress: (OriginalTransferProgress) -> Unit,
    ) = throw IOException("Original image service is unavailable")

    override suspend fun saveOriginal(
        assetId: String,
        destination: Uri,
        onProgress: (OriginalTransferProgress) -> Unit,
    ) = throw IOException("Original image service is unavailable")

    override fun deletePreparedOriginal(assetId: String) = Unit
    override fun clearPreparedOriginals() = Unit
}
