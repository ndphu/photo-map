package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.GalleryBatchAction
import com.photomap.app.data.gallery.GalleryBatchActions
import com.photomap.app.data.gallery.GalleryFilter
import com.photomap.app.data.gallery.GalleryListItem
import com.photomap.app.data.gallery.GalleryMediaType
import com.photomap.app.data.gallery.GalleryPager
import com.photomap.app.data.gallery.GalleryQuickFilter
import com.photomap.app.data.gallery.GallerySection
import com.photomap.app.data.gallery.timelineGroupKey
import com.photomap.app.data.gallery.timelineHeaderLabel
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.network.NetworkMonitor
import com.photomap.app.data.network.NetworkState
import com.photomap.app.data.repository.GallerySyncController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GallerySyncSummary(
    val pending: Int = 0,
    val uploading: Int = 0,
    val failed: Int = 0,
    val uploaded: Int = 0,
)

data class GalleryBatchProgress(
    val label: String,
    val completed: Int,
    val total: Int,
)

data class GalleryInteractionState(
    val selectedIds: Set<String> = emptySet(),
    val batchProgress: GalleryBatchProgress? = null,
    val resultMessage: String? = null,
    val albums: List<AlbumDto> = emptyList(),
    val showAlbumPicker: Boolean = false,
    val albumPickerLoading: Boolean = false,
    val canRetryBatch: Boolean = false,
)

data class GalleryUiState(
    val filter: GalleryFilter = GalleryFilter(),
    val networkState: NetworkState = NetworkState.UNKNOWN,
    val interaction: GalleryInteractionState = GalleryInteractionState(),
    val sync: GallerySyncSummary = GallerySyncSummary(),
)

enum class GalleryCommand {
    REFRESH,
    RETRY,
}

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val repository: GalleryPager,
    networkMonitor: NetworkMonitor,
    private val batchActions: GalleryBatchActions,
    private val syncController: GallerySyncController,
) : ViewModel() {
    private val _currentFilter = MutableStateFlow(GalleryFilter())
    val currentFilter: StateFlow<GalleryFilter> = _currentFilter.asStateFlow()

    private val _interaction = MutableStateFlow(GalleryInteractionState())
    val interactionState: StateFlow<GalleryInteractionState> = _interaction.asStateFlow()
    private var retryAction: GalleryBatchAction? = null

    private val _commands = MutableSharedFlow<GalleryCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    val networkState: StateFlow<NetworkState> = networkMonitor.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NetworkState.UNKNOWN,
    )

    private val syncSummary = combine(
        syncController.pendingCount,
        syncController.uploadingCount,
        syncController.failedCount,
        syncController.uploadedCount,
    ) { pending, uploading, failed, uploaded ->
        GallerySyncSummary(pending, uploading, failed, uploaded)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GallerySyncSummary(),
    )

    val uiState: StateFlow<GalleryUiState> = combine(
        currentFilter,
        networkState,
        _interaction,
        syncSummary,
    ) { filter, network, interaction, sync ->
        GalleryUiState(filter, network, interaction, sync)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState(),
    )

    val pagingData: Flow<PagingData<GalleryListItem>> = combine(
        currentFilter,
        repository.invalidationVersion,
    ) { filter, _ -> filter }
        .flatMapLatest(repository::getGalleryPager)
        .map { pagingData ->
            pagingData
                .map<AssetUiModel, GalleryListItem> { GalleryListItem.Asset(it) }
                .insertSeparators { before, after ->
                    val afterAsset = (after as? GalleryListItem.Asset)?.value ?: return@insertSeparators null
                    val beforeAsset = (before as? GalleryListItem.Asset)?.value
                    val afterGroup = timelineGroupKey(afterAsset.takenAt)
                    if (beforeAsset == null || timelineGroupKey(beforeAsset.takenAt) != afterGroup) {
                        GalleryListItem.DateHeader(afterGroup, timelineHeaderLabel(afterAsset.takenAt))
                    } else {
                        null
                    }
                }
        }
        .cachedIn(viewModelScope)

    init {
        syncController.uploadedCount
            .distinctUntilChanged()
            .drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        _commands.tryEmit(GalleryCommand.REFRESH)
    }

    fun retry() {
        _commands.tryEmit(GalleryCommand.RETRY)
    }

    fun onAssetTap(assetId: String): Boolean {
        if (_interaction.value.selectedIds.isEmpty()) return true
        toggleSelection(assetId)
        return false
    }

    fun selectAsset(assetId: String) {
        if (_interaction.value.batchProgress == null) {
            _interaction.update { it.copy(selectedIds = it.selectedIds + assetId, resultMessage = null) }
        }
    }

    fun clearSelection() {
        retryAction = null
        _interaction.update {
            it.copy(
                selectedIds = emptySet(),
                batchProgress = null,
                resultMessage = null,
                showAlbumPicker = false,
                canRetryBatch = false,
            )
        }
    }

    fun favoriteSelected() = executeBatch(GalleryBatchAction.Favorite)

    fun archiveSelected() = executeBatch(GalleryBatchAction.Archive)

    fun trashSelected() = executeBatch(GalleryBatchAction.Trash)

    fun retryFailedBatch() {
        retryAction?.let(::executeBatch)
    }

    fun showAlbumPicker() {
        if (_interaction.value.selectedIds.isEmpty() || _interaction.value.batchProgress != null) return
        viewModelScope.launch {
            _interaction.update {
                it.copy(albumPickerLoading = true, resultMessage = null, canRetryBatch = false)
            }
            try {
                val albums = batchActions.listAlbums()
                _interaction.update {
                    it.copy(albums = albums, showAlbumPicker = true, albumPickerLoading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _interaction.update {
                    it.copy(
                        albumPickerLoading = false,
                        resultMessage = "Cannot load albums",
                        canRetryBatch = false,
                    )
                }
            }
        }
    }

    fun dismissAlbumPicker() {
        _interaction.update { it.copy(showAlbumPicker = false) }
    }

    fun addSelectedToAlbum(albumId: String) {
        _interaction.update { it.copy(showAlbumPicker = false) }
        executeBatch(GalleryBatchAction.AddToAlbum(albumId))
    }

    fun dismissResult() {
        _interaction.update { it.copy(resultMessage = null, canRetryBatch = false) }
    }

    fun startSync() {
        viewModelScope.launch { syncController.scanAndSync() }
    }

    fun retryFailedUploads() {
        viewModelScope.launch { syncController.retryFailed() }
    }

    fun updateFilter(filter: GalleryFilter) {
        if (_currentFilter.value != filter) {
            clearSelection()
            _currentFilter.value = filter
        }
    }

    fun clearFilters() {
        updateFilter(GalleryFilter())
    }

    fun selectQuickFilter(filter: GalleryQuickFilter) {
        val current = _currentFilter.value
        updateFilter(
            current.copy(
                mediaType = when (filter) {
                    GalleryQuickFilter.PHOTOS -> GalleryMediaType.IMAGE
                    GalleryQuickFilter.VIDEOS -> GalleryMediaType.VIDEO
                    else -> GalleryMediaType.ALL
                },
                favoriteOnly = filter == GalleryQuickFilter.FAVORITES,
            ),
        )
    }

    fun showSection(section: GallerySection) {
        val current = _currentFilter.value
        updateFilter(
            when (section) {
                GallerySection.MAIN -> current.copy(archived = false, trashed = false)
                GallerySection.ARCHIVE -> current.copy(archived = true, trashed = false)
                GallerySection.TRASH -> current.copy(archived = null, trashed = true)
            },
        )
    }

    fun onAppForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundRefreshAt < FOREGROUND_REFRESH_GUARD_MILLIS) return
        lastForegroundRefreshAt = now
        refresh()
    }

    private fun toggleSelection(assetId: String) {
        _interaction.update { state ->
            val selected = if (assetId in state.selectedIds) {
                state.selectedIds - assetId
            } else {
                state.selectedIds + assetId
            }
            state.copy(selectedIds = selected, resultMessage = null)
        }
    }

    private fun executeBatch(action: GalleryBatchAction) {
        val selectedIds = _interaction.value.selectedIds
        if (
            selectedIds.isEmpty() ||
            _interaction.value.batchProgress != null ||
            _interaction.value.albumPickerLoading
        ) return
        retryAction = action
        viewModelScope.launch {
            val label = action.progressLabel
            _interaction.update {
                it.copy(
                    batchProgress = GalleryBatchProgress(label, 0, selectedIds.size),
                    resultMessage = null,
                    canRetryBatch = false,
                )
            }
            try {
                val result = batchActions.execute(selectedIds, action) { completed, total ->
                    _interaction.update { state ->
                        val currentCompleted = state.batchProgress?.completed ?: 0
                        state.copy(
                            batchProgress = GalleryBatchProgress(
                                label,
                                maxOf(completed, currentCompleted),
                                total,
                            ),
                        )
                    }
                }
                if (result.failedIds.isEmpty()) {
                    retryAction = null
                    _interaction.update {
                        it.copy(
                            selectedIds = emptySet(),
                            batchProgress = null,
                            resultMessage = "${result.succeeded} items updated",
                            canRetryBatch = false,
                        )
                    }
                } else {
                    _interaction.update {
                        it.copy(
                            selectedIds = result.failedIds,
                            batchProgress = null,
                            resultMessage = "${result.succeeded} succeeded, ${result.failedIds.size} failed",
                            canRetryBatch = true,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _interaction.update {
                    it.copy(
                        batchProgress = null,
                        resultMessage = "Some items failed",
                        canRetryBatch = true,
                    )
                }
            }
        }
    }

    private var lastForegroundRefreshAt = 0L

    private companion object {
        const val FOREGROUND_REFRESH_GUARD_MILLIS = 2_000L
    }
}

private val GalleryBatchAction.progressLabel: String
    get() = when (this) {
        GalleryBatchAction.Favorite -> "Favoriting"
        GalleryBatchAction.Archive -> "Archiving"
        GalleryBatchAction.Trash -> "Moving to trash"
        is GalleryBatchAction.AddToAlbum -> "Adding to album"
    }

class GalleryViewModelFactory(
    private val repository: GalleryPager,
    private val networkMonitor: NetworkMonitor,
    private val batchActions: GalleryBatchActions,
    private val syncController: GallerySyncController,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GalleryViewModel(repository, networkMonitor, batchActions, syncController) as T
}
