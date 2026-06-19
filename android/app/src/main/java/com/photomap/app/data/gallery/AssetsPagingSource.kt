package com.photomap.app.data.gallery

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.photomap.app.data.network.AssetItemDto
import com.photomap.app.data.network.AssetListResponse
import com.photomap.app.data.network.PhotoMapApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

const val GALLERY_PAGE_SIZE = 50

fun interface AssetsRemoteDataSource {
    suspend fun load(cursor: String?, limit: Int, filter: GalleryFilter): AssetListResponse
}

class RetrofitAssetsRemoteDataSource(
    private val api: PhotoMapApi,
) : AssetsRemoteDataSource {
    override suspend fun load(
        cursor: String?,
        limit: Int,
        filter: GalleryFilter,
    ): AssetListResponse = api.listAssets(
        limit = limit,
        cursor = cursor,
        mediaType = filter.mediaType.apiValue,
        favorite = true.takeIf { filter.favoriteOnly },
        archived = filter.archived,
        trashed = filter.trashed,
        city = filter.city,
        from = filter.from,
        to = filter.to,
    )
}

class AssetsPagingSource(
    private val remoteDataSource: AssetsRemoteDataSource,
    private val filter: GalleryFilter,
) : PagingSource<String, AssetUiModel>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, AssetUiModel> = try {
        val response = remoteDataSource.load(
            cursor = params.key,
            limit = params.loadSize.coerceAtMost(GALLERY_PAGE_SIZE),
            filter = filter,
        )
        LoadResult.Page(
            data = response.items.map(AssetItemDto::toUiModel),
            prevKey = null,
            nextKey = response.nextCursor,
        )
    } catch (error: HttpException) {
        LoadResult.Error(
            if (error.code() == HTTP_UNAUTHORIZED) UnauthorizedGalleryException(error) else error,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LoadResult.Error(error)
    }

    override fun getRefreshKey(state: PagingState<String, AssetUiModel>): String? = null

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

class UnauthorizedGalleryException(cause: HttpException) : Exception(
    "Authentication is required",
    cause,
)

fun AssetItemDto.toUiModel() = AssetUiModel(
    id = id,
    mediaType = mediaType,
    mimeType = mimeType,
    thumbnailUrl = thumbnailUrl,
    previewUrl = previewUrl,
    takenAt = takenAt,
    width = width,
    height = height,
    durationMs = durationMs,
    isFavorite = isFavorite,
)
