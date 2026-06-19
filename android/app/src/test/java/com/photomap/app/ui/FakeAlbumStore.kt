package com.photomap.app.ui

import com.photomap.app.data.albums.AlbumStore
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.network.AlbumDto

class FakeAlbumStore : AlbumStore {
    var albums = mutableListOf(testAlbum())
    var assets = mutableListOf(testAlbumAsset())
    var listError: Exception? = null
    var createError: Exception? = null
    var removeError: Exception? = null
    var deleteCalls = 0
    var removeCalls = 0

    override suspend fun listAlbums(): List<AlbumDto> {
        listError?.let { throw it }
        return albums.toList()
    }

    override suspend fun getAlbum(id: String): AlbumDto = albums.first { it.id == id }

    override suspend fun createAlbum(name: String, description: String?): AlbumDto {
        createError?.let { throw it }
        return testAlbum(id = "created", name = name, description = description).also(albums::add)
    }

    override suspend fun updateAlbum(id: String, name: String, description: String?): AlbumDto {
        val updated = albums.first { it.id == id }.copy(name = name, description = description)
        albums = albums.map { if (it.id == id) updated else it }.toMutableList()
        return updated
    }

    override suspend fun deleteAlbum(id: String) {
        deleteCalls += 1
        albums.removeAll { it.id == id }
    }

    override suspend fun listAssets(albumId: String): List<AssetUiModel> = assets.toList()

    override suspend fun addAsset(albumId: String, assetId: String) = Unit

    override suspend fun removeAsset(albumId: String, assetId: String) {
        removeError?.let { throw it }
        removeCalls += 1
        assets.removeAll { it.id == assetId }
    }
}

fun testAlbum(
    id: String = "album-id",
    name: String = "Trip",
    description: String? = "Summer",
) = AlbumDto(
    id = id,
    name = name,
    description = description,
    coverAssetId = null,
    isArchived = false,
    createdAt = "2026-06-19T00:00:00Z",
    updatedAt = "2026-06-19T00:00:00Z",
)

fun testAlbumAsset() = AssetUiModel(
    id = "asset-id",
    mediaType = "image",
    mimeType = "image/jpeg",
    thumbnailUrl = null,
    previewUrl = null,
    takenAt = "2026-06-19T00:00:00Z",
    width = 100,
    height = 100,
    durationMs = null,
    isFavorite = false,
)
