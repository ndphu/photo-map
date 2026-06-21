package com.photomap.app.data.gallery

const val GALLERY_ZOOM_IN_THRESHOLD = 1.18f
const val GALLERY_ZOOM_OUT_THRESHOLD = 0.85f

enum class GalleryGridZoomAction {
    DECREASE_COLUMNS,
    INCREASE_COLUMNS,
}

fun galleryGridZoomAction(accumulatedZoom: Float): GalleryGridZoomAction? = when {
    accumulatedZoom > GALLERY_ZOOM_IN_THRESHOLD -> GalleryGridZoomAction.DECREASE_COLUMNS
    accumulatedZoom < GALLERY_ZOOM_OUT_THRESHOLD -> GalleryGridZoomAction.INCREASE_COLUMNS
    else -> null
}
