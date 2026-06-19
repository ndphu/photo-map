package com.photomap.app.ui

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
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
    fun loadAssets() = runTest(dispatcher) {
        val viewModel = AlbumDetailViewModel("album-id", FakeAlbumStore())

        advanceUntilIdle()

        assertEquals("Trip", viewModel.state.value.album?.name)
        assertEquals(listOf("asset-id"), viewModel.state.value.assets.map { it.id })
    }

    @Test
    fun removeAssetSuccessAndFailure() = runTest(dispatcher) {
        val repository = FakeAlbumStore()
        val viewModel = AlbumDetailViewModel("album-id", repository)
        advanceUntilIdle()

        viewModel.removeAsset("asset-id")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.assets.isEmpty())
        assertEquals(1, repository.removeCalls)

        repository.assets += testAlbumAsset()
        repository.removeError = IOException("offline")
        viewModel.load()
        advanceUntilIdle()
        viewModel.removeAsset("asset-id")
        advanceUntilIdle()
        assertEquals("Cannot remove asset from album", viewModel.state.value.error)
        assertEquals(listOf("asset-id"), viewModel.state.value.assets.map { it.id })
    }
}
