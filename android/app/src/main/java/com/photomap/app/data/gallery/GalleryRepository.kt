package com.photomap.app.data.gallery

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GalleryInvalidator {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    fun invalidate() {
        _version.value += 1L
    }
}

interface GalleryPager {
    val invalidationVersion: StateFlow<Long>
    fun getGalleryPager(filter: GalleryFilter): Flow<PagingData<AssetUiModel>>
}

class GalleryRepository(
    private val remoteDataSource: AssetsRemoteDataSource,
    invalidator: GalleryInvalidator,
) : GalleryPager {
    override val invalidationVersion: StateFlow<Long> = invalidator.version

    override fun getGalleryPager(filter: GalleryFilter): Flow<PagingData<AssetUiModel>> = Pager(
        config = PagingConfig(
            pageSize = GALLERY_PAGE_SIZE,
            initialLoadSize = GALLERY_PAGE_SIZE,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { AssetsPagingSource(remoteDataSource, filter) },
    ).flow
}
