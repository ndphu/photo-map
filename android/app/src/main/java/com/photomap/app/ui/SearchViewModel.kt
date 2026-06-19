package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.search.SearchPager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val repository: SearchPager) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val query = MutableStateFlow("")
    private val retryVersion = MutableStateFlow(0L)

    private val submittedQueries = query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()

    val pagingData: Flow<PagingData<AssetUiModel>> = combine(
        submittedQueries,
        retryVersion,
    ) { submitted, _ -> submitted }
        .onEach { submitted ->
            _uiState.update { it.copy(submittedQuery = submitted, error = null) }
        }
        .flatMapLatest { submitted ->
            if (submitted.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                repository.search(submitted).catch {
                    _uiState.update { state -> state.copy(error = "Cannot search right now") }
                    emit(PagingData.empty())
                }
            }
        }
        .cachedIn(viewModelScope)

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value, error = null) }
        query.value = value
    }

    fun clearQuery() = updateQuery("")

    fun retry() {
        _uiState.update { it.copy(error = null) }
        retryVersion.value += 1
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}

class SearchViewModelFactory(private val repository: SearchPager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(repository) as T
}
