package com.photomap.app.data.repository

import com.photomap.app.data.gallery.GalleryInvalidator
import com.photomap.app.data.network.ArchiveRequest
import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.network.FavoriteRequest
import com.photomap.app.data.network.PhotoMapApi

interface AssetRemoteDataSource {
    suspend fun detail(id: String): AssetDetailDto
    suspend fun readUrl(id: String, variant: String): String
    suspend fun favorite(id: String, favorite: Boolean): AssetDetailDto
    suspend fun archive(id: String, archived: Boolean): AssetDetailDto
    suspend fun trash(id: String): AssetDetailDto
    suspend fun restore(id: String): AssetDetailDto
    suspend fun delete(id: String)
}

class RetrofitAssetRemoteDataSource(private val api: PhotoMapApi) : AssetRemoteDataSource {
    override suspend fun detail(id: String): AssetDetailDto = api.getAsset(id)

    override suspend fun readUrl(id: String, variant: String): String =
        api.getReadUrl(id, variant).url

    override suspend fun favorite(id: String, favorite: Boolean): AssetDetailDto =
        api.updateFavorite(id, FavoriteRequest(favorite))

    override suspend fun archive(id: String, archived: Boolean): AssetDetailDto =
        api.updateArchive(id, ArchiveRequest(archived))

    override suspend fun trash(id: String): AssetDetailDto = api.trashAsset(id)

    override suspend fun restore(id: String): AssetDetailDto = api.restoreAsset(id)

    override suspend fun delete(id: String) = api.deleteAsset(id)
}

class AssetRepository(
    private val remoteDataSource: AssetRemoteDataSource,
    private val galleryInvalidator: GalleryInvalidator,
) {
    suspend fun detail(id: String): AssetDetailDto = remoteDataSource.detail(id)

    suspend fun readUrl(id: String, variant: String): String = remoteDataSource.readUrl(id, variant)

    suspend fun previewUrl(id: String): String = readUrl(id, PREVIEW_VARIANT)

    suspend fun originalUrl(id: String): String = readUrl(id, ORIGINAL_VARIANT)

    suspend fun setFavorite(
        id: String,
        favorite: Boolean,
        invalidateGallery: Boolean = true,
    ): AssetDetailDto = remoteDataSource.favorite(id, favorite).also {
        if (invalidateGallery) galleryInvalidator.invalidate()
    }

    suspend fun setArchived(
        id: String,
        archived: Boolean,
        invalidateGallery: Boolean = true,
    ): AssetDetailDto = remoteDataSource.archive(id, archived).also {
        if (invalidateGallery) galleryInvalidator.invalidate()
    }

    suspend fun trash(id: String, invalidateGallery: Boolean = true): AssetDetailDto =
        remoteDataSource.trash(id).also {
            if (invalidateGallery) galleryInvalidator.invalidate()
        }

    suspend fun restore(id: String): AssetDetailDto =
        remoteDataSource.restore(id).also { galleryInvalidator.invalidate() }

    suspend fun delete(id: String) {
        remoteDataSource.delete(id)
        galleryInvalidator.invalidate()
    }

    fun invalidateGallery() {
        galleryInvalidator.invalidate()
    }

    private companion object {
        const val PREVIEW_VARIANT = "preview"
        const val ORIGINAL_VARIANT = "original"
    }
}
