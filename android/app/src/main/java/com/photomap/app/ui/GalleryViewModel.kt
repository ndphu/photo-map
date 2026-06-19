package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.network.AssetItemDto
import com.photomap.app.data.repository.AssetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val items: List<AssetItemDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class GalleryViewModel(private val repository: AssetRepository) : ViewModel() {
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        load(cursor = null, replace = true)
    }

    fun loadNext() {
        val cursor = _state.value.nextCursor ?: return
        load(cursor, replace = false)
    }

    private fun load(cursor: String?, replace: Boolean) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.list(cursor) }
                .onSuccess { response ->
                    _state.value = GalleryUiState(
                        items = if (replace) response.items else _state.value.items + response.items,
                        nextCursor = response.nextCursor,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Unable to load gallery",
                    )
                }
        }
    }
}

class GalleryViewModelFactory(
    private val repository: AssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GalleryViewModel(repository) as T
}
