package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.network.AlbumDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: AlbumDto? = null,
    val assets: List<AssetUiModel> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class AlbumDetailViewModel(
    private val albumId: String,
    private val repository: AlbumStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val album = async { repository.getAlbum(albumId) }
                val assets = async { repository.listAssets(albumId) }
                _state.value = AlbumDetailUiState(album.await(), assets.await(), loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Cannot load albums")
            }
        }
    }

    fun removeAsset(assetId: String) {
        viewModelScope.launch {
            try {
                repository.removeAsset(albumId, assetId)
                _state.value = _state.value.copy(
                    assets = _state.value.assets.filterNot { it.id == assetId },
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Cannot remove asset from album")
            }
        }
    }
}

class AlbumDetailViewModelFactory(
    private val albumId: String,
    private val repository: AlbumStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AlbumDetailViewModel(albumId, repository) as T
}
