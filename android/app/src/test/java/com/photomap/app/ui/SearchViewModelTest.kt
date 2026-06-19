package com.photomap.app.ui

import androidx.paging.PagingData
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.search.SearchPager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun blankQueryDoesNotSearch() = runTest(dispatcher) {
        val repository = FakeSearchPager()
        val viewModel = SearchViewModel(repository)
        backgroundScope.launch { viewModel.pagingData.collect() }

        advanceTimeBy(500)
        runCurrent()

        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun debounceSubmitsLatestQuery() = runTest(dispatcher) {
        val repository = FakeSearchPager()
        val viewModel = SearchViewModel(repository)
        backgroundScope.launch { viewModel.pagingData.collect() }

        viewModel.updateQuery("motor")
        advanceTimeBy(399)
        runCurrent()
        assertTrue(repository.queries.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("motor"), repository.queries)
    }

    @Test
    fun repositoryErrorIsSurfaced() = runTest(dispatcher) {
        val repository = FakeSearchPager(error = IOException("offline"))
        val viewModel = SearchViewModel(repository)
        backgroundScope.launch { viewModel.pagingData.collect() }

        viewModel.updateQuery("motorcycle")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals("Cannot search right now", viewModel.uiState.value.error)
    }
}

private class FakeSearchPager(private val error: Exception? = null) : SearchPager {
    val queries = mutableListOf<String>()

    override fun search(query: String): Flow<PagingData<AssetUiModel>> {
        queries += query
        return error?.let { failure ->
            flow<PagingData<AssetUiModel>> { throw failure }
        } ?: flowOf(PagingData.empty())
    }
}
