package com.photomap.app.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ZoomablePhotoViewer(
    imageUrl: String,
    contentDescription: String?,
    assetKey: String,
    modifier: Modifier = Modifier,
    isRefreshingUrl: Boolean = false,
    onImageLoadFailed: () -> Unit,
    onImageLoaded: () -> Unit,
    onRetry: () -> Unit,
) {
    val currentContentKey = "$assetKey:${imageUrl.hashCode()}"
    var previousContentKey by remember(assetKey) { mutableStateOf(currentContentKey) }
    var scale by rememberSaveable(assetKey) { mutableFloatStateOf(MIN_PHOTO_SCALE) }
    var offsetX by rememberSaveable(assetKey) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(assetKey) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var loading by remember(imageUrl) { mutableStateOf(true) }
    var failed by remember(imageUrl) { mutableStateOf(false) }

    LaunchedEffect(currentContentKey) {
        val reset = resetTransformForContentChange(
            previousContentKey = previousContentKey,
            nextContentKey = currentContentKey,
            transform = PhotoTransform(scale, Offset(offsetX, offsetY)),
        )
        scale = reset.scale
        offsetX = reset.offset.x
        offsetY = reset.offset.y
        previousContentKey = currentContentKey
    }

    LaunchedEffect(containerSize, scale) {
        val clamped = clampPhotoOffset(Offset(offsetX, offsetY), scale, containerSize)
        offsetX = clamped.x
        offsetY = clamped.y
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(assetKey, imageUrl, containerSize) {
                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                    val next = applyPhotoGesture(
                        transform = PhotoTransform(scale, Offset(offsetX, offsetY)),
                        zoomChange = zoom,
                        panChange = pan,
                        containerSize = containerSize,
                    )
                    scale = next.scale
                    offsetX = next.offset.x
                    offsetY = next.offset.y
                }
            }
            .pointerInput(assetKey, imageUrl, containerSize) {
                detectTapGestures(
                    onDoubleTap = {
                        val next = togglePhotoZoom(
                            PhotoTransform(scale, Offset(offsetX, offsetY)),
                            containerSize,
                        )
                        scale = next.scale
                        offsetX = next.offset.x
                        offsetY = next.offset.y
                    },
                )
            },
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onLoading = {
                loading = true
                failed = false
            },
            onSuccess = {
                loading = false
                failed = false
                onImageLoaded()
            },
            onError = {
                loading = false
                failed = true
                onImageLoadFailed()
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )

        if (loading || isRefreshingUrl) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        if (failed && !isRefreshingUrl) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Cannot load photo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
