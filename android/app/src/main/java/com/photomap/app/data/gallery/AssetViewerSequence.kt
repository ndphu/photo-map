package com.photomap.app.data.gallery

import kotlinx.coroutines.flow.Flow

data class ViewerAssetSummary(
    val id: String,
    val mediaType: String?,
    val originalFilename: String?,
    val thumbnailUrl: String?,
    val previewUrl: String?,
)

fun interface AssetViewerSequence {
    fun observeViewerAssets(): Flow<List<ViewerAssetSummary>>
}
