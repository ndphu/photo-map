package com.photomap.app.data.search

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.GALLERY_PAGE_SIZE
import com.photomap.app.data.gallery.UnauthorizedGalleryException
import com.photomap.app.data.gallery.toUiModel
import com.photomap.app.data.network.AssetListResponse
import com.photomap.app.data.network.PhotoMapApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

fun interface SearchRemoteDataSource {
    suspend fun search(query: String, cursor: String?, limit: Int): AssetListResponse
}

class RetrofitSearchRemoteDataSource(private val api: PhotoMapApi) : SearchRemoteDataSource {
    override suspend fun search(query: String, cursor: String?, limit: Int): AssetListResponse =
        api.searchAssets(query = query, limit = limit, cursor = cursor)
}

interface SearchPager {
    fun search(query: String): Flow<PagingData<AssetUiModel>>
}

class SearchRepository(private val remoteDataSource: SearchRemoteDataSource) : SearchPager {
    override fun search(query: String): Flow<PagingData<AssetUiModel>> = Pager(
        config = PagingConfig(
            pageSize = GALLERY_PAGE_SIZE,
            initialLoadSize = GALLERY_PAGE_SIZE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { SearchPagingSource(remoteDataSource, query) },
    ).flow
}

class SearchPagingSource(
    private val remoteDataSource: SearchRemoteDataSource,
    private val query: String,
) : PagingSource<String, AssetUiModel>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, AssetUiModel> = try {
        val response = remoteDataSource.search(
            query = query,
            cursor = params.key,
            limit = params.loadSize.coerceAtMost(GALLERY_PAGE_SIZE),
        )
        LoadResult.Page(
            data = response.items.map { it.toUiModel() },
            prevKey = null,
            nextKey = response.nextCursor,
        )
    } catch (error: HttpException) {
        LoadResult.Error(if (error.code() == 401) UnauthorizedGalleryException(error) else error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LoadResult.Error(error)
    }

    override fun getRefreshKey(state: PagingState<String, AssetUiModel>): String? = null
}
