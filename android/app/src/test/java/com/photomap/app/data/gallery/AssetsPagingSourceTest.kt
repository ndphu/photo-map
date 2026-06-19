package com.photomap.app.data.gallery

import androidx.paging.PagingSource
import com.photomap.app.data.network.AssetItemDto
import com.photomap.app.data.network.AssetListResponse
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AssetsPagingSourceTest {
    @Test
    fun firstPageSuccess() = runTest {
        val source = pagingSource { cursor, _, _ ->
            assertEquals(null, cursor)
            AssetListResponse(listOf(asset("one")), "next")
        }

        val result = source.load(refresh())

        assertEquals(
            PagingSource.LoadResult.Page(
                data = listOf(assetUi("one")),
                prevKey = null,
                nextKey = "next",
            ),
            result,
        )
    }

    @Test
    fun nextPageSuccess() = runTest {
        val source = pagingSource { cursor, _, _ ->
            assertEquals("next", cursor)
            AssetListResponse(listOf(asset("two")), null)
        }

        val result = source.load(append("next"))

        assertEquals(
            PagingSource.LoadResult.Page(
                data = listOf(assetUi("two")),
                prevKey = null,
                nextKey = null,
            ),
            result,
        )
    }

    @Test
    fun emptyResponseEndsPagination() = runTest {
        val source = pagingSource { _, _, _ -> AssetListResponse(emptyList(), null) }

        val result = source.load(refresh()) as PagingSource.LoadResult.Page<String, AssetUiModel>

        assertTrue(result.data.isEmpty())
        assertEquals(null, result.nextKey)
    }

    @Test
    fun networkErrorIsReturned() = runTest {
        val source = pagingSource { _, _, _ -> throw IOException("offline") }

        val result = source.load(refresh()) as PagingSource.LoadResult.Error<String, AssetUiModel>

        assertTrue(result.throwable is IOException)
    }

    @Test
    fun unauthorizedIsDistinguished() = runTest {
        val source = pagingSource { _, _, _ ->
            throw HttpException(
                Response.error<Any>(
                    401,
                    "{}".toResponseBody("application/json".toMediaType()),
                ),
            )
        }

        val result = source.load(refresh()) as PagingSource.LoadResult.Error<String, AssetUiModel>

        assertTrue(result.throwable is UnauthorizedGalleryException)
    }

    private fun pagingSource(load: suspend (String?, Int, GalleryFilter) -> AssetListResponse) =
        AssetsPagingSource(AssetsRemoteDataSource(load), GalleryFilter())

    private fun refresh() = PagingSource.LoadParams.Refresh<String>(
        key = null,
        loadSize = GALLERY_PAGE_SIZE,
        placeholdersEnabled = false,
    )

    private fun append(key: String) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = GALLERY_PAGE_SIZE,
        placeholdersEnabled = false,
    )

    private fun asset(id: String) = AssetItemDto(
        id = id,
        mediaType = "image",
        mimeType = "image/jpeg",
        thumbnailKey = null,
        previewKey = null,
        thumbnailUrl = "https://signed.example/$id",
        previewUrl = null,
        takenAt = null,
        width = 100,
        height = 100,
        durationMs = null,
        isFavorite = false,
    )

    private fun assetUi(id: String) = AssetUiModel(
        id = id,
        mediaType = "image",
        mimeType = "image/jpeg",
        thumbnailUrl = "https://signed.example/$id",
        previewUrl = null,
        takenAt = null,
        width = 100,
        height = 100,
        durationMs = null,
        isFavorite = false,
    )
}
