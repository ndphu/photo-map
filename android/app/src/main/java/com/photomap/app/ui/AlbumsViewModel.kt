package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.network.AlbumDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumEditorState(
    val albumId: String? = null,
    val name: String = "",
    val description: String = "",
)

data class AlbumsUiState(
    val albums: List<AlbumDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val editor: AlbumEditorState? = null,
    val deleteCandidate: AlbumDto? = null,
)

class AlbumsViewModel(private val repository: AlbumStore) : ViewModel() {
    private val _state = MutableStateFlow(AlbumsUiState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value.loading && _state.value.albums.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                _state.value = _state.value.copy(albums = repository.listAlbums(), loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Cannot load albums")
            }
        }
    }

    fun showCreate() {
        _state.value = _state.value.copy(editor = AlbumEditorState(), error = null)
    }

    fun showEdit(album: AlbumDto) {
        _state.value = _state.value.copy(
            editor = AlbumEditorState(album.id, album.name, album.description.orEmpty()),
            error = null,
        )
    }

    fun dismissEditor() {
        _state.value = _state.value.copy(editor = null)
    }

    fun saveAlbum(name: String, description: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val editor = _state.value.editor ?: return
        viewModelScope.launch {
            try {
                val saved = if (editor.albumId == null) {
                    repository.createAlbum(trimmedName, description.trim().ifEmpty { null })
                } else {
                    repository.updateAlbum(editor.albumId, trimmedName, description.trim().ifEmpty { null })
                }
                val albums = if (editor.albumId == null) {
                    listOf(saved) + _state.value.albums
                } else {
                    _state.value.albums.map { if (it.id == saved.id) saved else it }
                }
                _state.value = _state.value.copy(albums = albums, editor = null, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    error = if (editor.albumId == null) "Cannot create album" else "Cannot update album",
                )
            }
        }
    }

    fun requestDelete(album: AlbumDto) {
        _state.value = _state.value.copy(deleteCandidate = album)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(deleteCandidate = null)
    }

    fun confirmDelete() {
        val album = _state.value.deleteCandidate ?: return
        viewModelScope.launch {
            try {
                repository.deleteAlbum(album.id)
                _state.value = _state.value.copy(
                    albums = _state.value.albums.filterNot { it.id == album.id },
                    deleteCandidate = null,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(deleteCandidate = null, error = "Cannot delete album")
            }
        }
    }
}

class AlbumsViewModelFactory(private val repository: AlbumStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AlbumsViewModel(repository) as T
}
