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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
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
    fun loadAlbums() = runTest(dispatcher) {
        val viewModel = AlbumsViewModel(FakeAlbumStore())

        advanceUntilIdle()

        assertEquals(listOf("Trip"), viewModel.state.value.albums.map { it.name })
    }

    @Test
    fun createAlbumSuccessAndFailure() = runTest(dispatcher) {
        val repository = FakeAlbumStore()
        val viewModel = AlbumsViewModel(repository)
        advanceUntilIdle()

        viewModel.showCreate()
        viewModel.saveAlbum("Family", "")
        advanceUntilIdle()
        assertEquals("Family", viewModel.state.value.albums.first().name)
        assertNull(viewModel.state.value.editor)

        repository.createError = IOException("offline")
        viewModel.showCreate()
        viewModel.saveAlbum("Work", "")
        advanceUntilIdle()
        assertEquals("Cannot create album", viewModel.state.value.error)
    }

    @Test
    fun deleteRequiresConfirmation() = runTest(dispatcher) {
        val repository = FakeAlbumStore()
        val viewModel = AlbumsViewModel(repository)
        advanceUntilIdle()
        val album = viewModel.state.value.albums.single()

        viewModel.requestDelete(album)
        assertEquals(album, viewModel.state.value.deleteCandidate)
        assertEquals(0, repository.deleteCalls)

        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.state.value.albums.isEmpty())
    }
}
