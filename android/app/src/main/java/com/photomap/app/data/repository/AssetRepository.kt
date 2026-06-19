package com.photomap.app.data.repository

import com.photomap.app.data.network.AssetDetailDto
import com.photomap.app.data.network.AssetListResponse
import com.photomap.app.data.network.FavoriteRequest
import com.photomap.app.data.network.PhotoMapApi

class AssetRepository(private val api: PhotoMapApi) {
    suspend fun list(cursor: String?): AssetListResponse = api.listAssets(limit = 50, cursor = cursor)

    suspend fun detail(id: String): AssetDetailDto = api.getAsset(id)

    suspend fun previewUrl(id: String): String = api.getReadUrl(id, "preview").url

    suspend fun setFavorite(id: String, favorite: Boolean): AssetDetailDto =
        api.updateFavorite(id, FavoriteRequest(favorite))

    suspend fun trash(id: String): AssetDetailDto = api.trashAsset(id)
}
