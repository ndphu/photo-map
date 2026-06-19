package com.photomap.app.data.albums

import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.toUiModel
import com.photomap.app.data.network.AddAssetToAlbumRequest
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.data.network.CreateAlbumRequest
import com.photomap.app.data.network.PhotoMapApi
import com.photomap.app.data.network.UpdateAlbumRequest

interface AlbumStore {
    suspend fun listAlbums(): List<AlbumDto>
    suspend fun getAlbum(id: String): AlbumDto
    suspend fun createAlbum(name: String, description: String?): AlbumDto
    suspend fun updateAlbum(id: String, name: String, description: String?): AlbumDto
    suspend fun deleteAlbum(id: String)
    suspend fun listAssets(albumId: String): List<AssetUiModel>
    suspend fun addAsset(albumId: String, assetId: String)
    suspend fun removeAsset(albumId: String, assetId: String)
}

class AlbumRepository(private val api: PhotoMapApi) : AlbumStore {
    override suspend fun listAlbums(): List<AlbumDto> = api.listAlbums().items

    override suspend fun getAlbum(id: String): AlbumDto = api.getAlbum(id)

    override suspend fun createAlbum(name: String, description: String?): AlbumDto =
        api.createAlbum(CreateAlbumRequest(name, description))

    override suspend fun updateAlbum(id: String, name: String, description: String?): AlbumDto =
        api.updateAlbum(id, UpdateAlbumRequest(name = name, description = description))

    override suspend fun deleteAlbum(id: String) = api.deleteAlbum(id)

    override suspend fun listAssets(albumId: String): List<AssetUiModel> =
        api.listAlbumAssets(albumId).items.map { it.toUiModel() }

    override suspend fun addAsset(albumId: String, assetId: String) =
        api.addAssetToAlbum(albumId, AddAssetToAlbumRequest(assetId))

    override suspend fun removeAsset(albumId: String, assetId: String) =
        api.removeAssetFromAlbum(albumId, assetId)
}
