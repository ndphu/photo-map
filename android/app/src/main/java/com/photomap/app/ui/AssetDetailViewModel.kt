package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.repository.AssetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class AssetDetailUiState(
    val asset: AssetDetailDto? = null,
    val previewUrl: String? = null,
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val error: String? = null,
    val previewLoadFailed: Boolean = false,
    val isRefreshingUrl: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showAlbumPicker: Boolean = false,
    val albums: List<AlbumDto> = emptyList(),
)

enum class AssetDetailEvent {
    NAVIGATE_BACK,
}

class AssetDetailViewModel(
    assetId: String,
    private val repository: AssetRepository,
    private val albumRepository: AlbumStore,
) : ViewModel() {
    private var activeAssetId = assetId
    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AssetDetailEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        load()
    }

    fun toggleFavorite() {
        val asset = _state.value.asset ?: return
        mutate("Cannot update asset") { repository.setFavorite(activeAssetId, !asset.isFavorite) }
    }

    fun toggleArchive() {
        val asset = _state.value.asset ?: return
        mutate("Cannot update asset", navigateBack = true) {
            repository.setArchived(activeAssetId, !asset.isArchived)
        }
    }

    fun trash() {
        mutate("Cannot update asset", navigateBack = true) { repository.trash(activeAssetId) }
    }

    fun restore() {
        mutate("Cannot update asset", navigateBack = true) { repository.restore(activeAssetId) }
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

    fun refreshReadUrl() {
        if (_state.value.actionInProgress || _state.value.isRefreshingUrl || _state.value.asset == null) return
        viewModelScope.launch { loadPreviewUrl(activeAssetId) }
    }

    fun loadAsset(assetId: String) {
        if (assetId == activeAssetId) return
        activeAssetId = assetId
        _state.value = AssetDetailUiState()
        load()
    }

    fun onPreviewLoadFailed() {
        _state.value = _state.value.copy(previewLoadFailed = true)
    }

    fun onPreviewLoaded() {
        _state.value = _state.value.copy(previewLoadFailed = false)
    }

    private fun load() {
        viewModelScope.launch {
            val requestedAssetId = activeAssetId
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val asset = repository.detail(requestedAssetId)
                if (requestedAssetId != activeAssetId) return@launch
                _state.value = _state.value.copy(asset = asset, loading = false)
                loadPreviewUrl(requestedAssetId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestedAssetId != activeAssetId) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    error = userFacingError(error, "Server unavailable"),
                )
            }
        }
    }

    private suspend fun loadPreviewUrl(requestedAssetId: String) {
        _state.value = _state.value.copy(isRefreshingUrl = true, error = null)
        try {
            val url = repository.previewUrl(requestedAssetId)
            if (requestedAssetId != activeAssetId) return
            _state.value = _state.value.copy(
                previewUrl = url,
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
        action: suspend () -> AssetDetailDto,
    ) {
        if (_state.value.asset == null || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, error = null)
            try {
                val updated = action()
                _state.value = _state.value.copy(asset = updated, actionInProgress = false)
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
}

private fun userFacingError(error: Exception, fallback: String): String = when {
    error is HttpException && error.code() == 401 -> "Session expired"
    error is IOException -> "Network unavailable"
    error is HttpException && error.code() >= 500 -> "Server unavailable"
    else -> fallback
}

class AssetDetailViewModelFactory(
    private val assetId: String,
    private val repository: AssetRepository,
    private val albumRepository: AlbumStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetId, repository, albumRepository) as T
}
