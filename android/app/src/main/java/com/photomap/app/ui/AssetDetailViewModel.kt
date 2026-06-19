package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.repository.AssetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val asset: AssetDetailDto? = null,
    val previewUrl: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val trashed: Boolean = false,
)

class AssetDetailViewModel(
    private val assetId: String,
    private val repository: AssetRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun toggleFavorite() {
        val current = _state.value.asset ?: return
        viewModelScope.launch {
            runCatching { repository.setFavorite(assetId, !current.isFavorite) }
                .onSuccess { _state.value = _state.value.copy(asset = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun trash() {
        viewModelScope.launch {
            runCatching { repository.trash(assetId) }
                .onSuccess { _state.value = _state.value.copy(asset = it, trashed = true) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun retry() {
        if (_state.value.loading) return
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                repository.detail(assetId) to repository.previewUrl(assetId)
            }.onSuccess { (asset, url) ->
                _state.value = AssetDetailUiState(asset = asset, previewUrl = url, loading = false)
            }.onFailure {
                _state.value = AssetDetailUiState(loading = false, error = it.message)
            }
        }
    }
}

class AssetDetailViewModelFactory(
    private val assetId: String,
    private val repository: AssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetId, repository) as T
}
