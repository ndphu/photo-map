package com.photomap.app.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class PhotoTransform(
    val scale: Float = MIN_PHOTO_SCALE,
    val offset: Offset = Offset.Zero,
)

fun applyPhotoGesture(
    transform: PhotoTransform,
    zoomChange: Float,
    panChange: Offset,
    containerSize: Size,
): PhotoTransform {
    val nextScale = (transform.scale * zoomChange).coerceIn(MIN_PHOTO_SCALE, MAX_PHOTO_SCALE)
    if (nextScale <= MIN_PHOTO_SCALE) return PhotoTransform()

    val nextOffset = if (transform.scale > MIN_PHOTO_SCALE || nextScale > MIN_PHOTO_SCALE) {
        transform.offset + panChange
    } else {
        transform.offset
    }
    return PhotoTransform(nextScale, clampPhotoOffset(nextOffset, nextScale, containerSize))
}

fun togglePhotoZoom(transform: PhotoTransform, containerSize: Size): PhotoTransform =
    if (transform.scale <= MIN_PHOTO_SCALE + SCALE_EPSILON) {
        PhotoTransform(DOUBLE_TAP_PHOTO_SCALE, clampPhotoOffset(Offset.Zero, DOUBLE_TAP_PHOTO_SCALE, containerSize))
    } else {
        PhotoTransform()
    }

fun clampPhotoOffset(offset: Offset, scale: Float, containerSize: Size): Offset {
    if (scale <= MIN_PHOTO_SCALE || containerSize.width <= 0f || containerSize.height <= 0f) {
        return Offset.Zero
    }
    val maxX = containerSize.width * (scale - MIN_PHOTO_SCALE) / 2f
    val maxY = containerSize.height * (scale - MIN_PHOTO_SCALE) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

fun resetTransformForContentChange(
    previousContentKey: String,
    nextContentKey: String,
    transform: PhotoTransform,
): PhotoTransform = if (previousContentKey == nextContentKey) transform else PhotoTransform()

const val MIN_PHOTO_SCALE = 1f
const val MAX_PHOTO_SCALE = 5f
private const val DOUBLE_TAP_PHOTO_SCALE = 2.5f
private const val SCALE_EPSILON = 0.05f
