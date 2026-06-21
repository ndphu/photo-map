package com.photomap.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.photomap.app.ui.AssetDetailUiState
import com.photomap.app.ui.OriginalImageStatus
import com.photomap.app.data.gallery.ViewerAssetSummary
import com.photomap.app.data.cache.CloudImageVariant
import com.photomap.app.data.cache.cloudImageRequest
import com.photomap.app.ui.viewer.ZoomablePhotoViewer
import com.photomap.app.ui.viewer.FullResolutionImageViewer
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    state: AssetDetailUiState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRetry: () -> Unit,
    onRetryPreview: () -> Unit,
    onPreviewError: () -> Unit,
    onPreviewLoaded: () -> Unit,
    onShowAlbumPicker: () -> Unit,
    onDismissAlbumPicker: () -> Unit,
    onAddToAlbum: (String) -> Unit,
    onOpenDetails: () -> Unit,
    onCloseDetails: () -> Unit,
    onAssetChanged: (String) -> Unit,
    onLoadOriginal: () -> Unit,
    onUsePreview: () -> Unit,
    onDownloadOriginal: (Uri) -> Unit,
    onOriginalMessageShown: () -> Unit,
) {
    val asset = state.asset
    val context = LocalContext.current
    val activeViewerAsset = state.viewerAssets.firstOrNull { it.id == state.activeAssetId }
    val assetTitle = asset?.originalFilename
        ?: activeViewerAsset?.originalFilename
        ?: "Asset"
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let(onDownloadOriginal)
    }

    LaunchedEffect(state.originalMessage) {
        val message = state.originalMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onOriginalMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = assetTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDetails,
                        enabled = asset != null,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = "Details")
                    }
                    IconButton(
                        onClick = onFavorite,
                        enabled = asset != null && !state.actionInProgress,
                    ) {
                        Icon(
                            if (asset?.isFavorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (asset?.isFavorite == true) "Unfavorite" else "Favorite",
                        )
                    }
                    DetailActionsMenu(
                        state = state,
                        onArchive = onArchive,
                        onTrash = onTrash,
                        onRestore = onRestore,
                        onDelete = onRequestDelete,
                        onAddToAlbum = onShowAlbumPicker,
                        onLoadOriginal = onLoadOriginal,
                        onUsePreview = onUsePreview,
                        onDownloadOriginal = {
                            downloadLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(asset?.mimeType ?: "image/*")
                                    .putExtra(Intent.EXTRA_TITLE, downloadFilename(asset?.originalFilename)),
                            )
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.activeAssetId != null) {
                    AssetViewerPager(
                        state = state,
                        onAssetChanged = onAssetChanged,
                        onPreviewError = onPreviewError,
                        onPreviewLoaded = onPreviewLoaded,
                        onRetryPreview = onRetryPreview,
                    )
                }
                val hasPreview = state.previewUrl != null || state.viewerAssets
                    .firstOrNull { it.id == state.activeAssetId }
                    ?.let { it.previewUrl != null || it.thumbnailUrl != null } == true
                if ((state.loading && asset == null && !hasPreview) || state.actionInProgress) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                if (
                    state.originalStatus == OriginalImageStatus.LOADING ||
                    state.originalStatus == OriginalImageStatus.DOWNLOADING
                ) {
                    val totalBytes = state.originalTotalBytes
                    if (totalBytes != null && totalBytes > 0L) {
                        CircularProgressIndicator(
                            progress = {
                                (state.originalTransferredBytes.toFloat() / totalBytes)
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                        )
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }

            state.error?.let { error ->
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    if (asset == null) {
                        Button(onClick = onRetry, enabled = !state.loading) { Text("Retry") }
                    }
                }
            }
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Permanently delete asset?") },
            text = { Text("This permanently deletes the cloud asset and cannot be undone. The local MediaStore file is not deleted.") },
            confirmButton = {
                Button(onClick = onConfirmDelete) { Text("Delete permanently") }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text("Cancel") }
            },
        )
    }

    if (state.showAlbumPicker) {
        AlertDialog(
            onDismissRequest = onDismissAlbumPicker,
            title = { Text("Add to album") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.albums.isEmpty()) {
                        Text("No albums yet. Create one from the Albums screen.")
                    } else {
                        state.albums.forEach { album ->
                            TextButton(
                                onClick = { onAddToAlbum(album.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(album.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissAlbumPicker) { Text("Close") }
            },
        )
    }

    if (state.showDetails && asset != null) {
        AssetDetailsBottomSheet(
            asset = asset,
            previewUrl = state.previewUrl,
            onDismiss = onCloseDetails,
            onCopy = { label, value -> copyDetailValue(context, label, value) },
            onOpenMaps = { latitude, longitude -> openAssetLocation(context, latitude, longitude) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetViewerPager(
    state: AssetDetailUiState,
    onAssetChanged: (String) -> Unit,
    onPreviewError: () -> Unit,
    onPreviewLoaded: () -> Unit,
    onRetryPreview: () -> Unit,
) {
    val activeAssetId = state.activeAssetId ?: return
    val currentAsset = state.asset
    val viewerAssets = state.viewerAssets.ifEmpty {
        listOf(
            ViewerAssetSummary(
                id = activeAssetId,
                mediaType = currentAsset?.mediaType,
                originalFilename = currentAsset?.originalFilename,
                thumbnailUrl = null,
                previewUrl = state.previewUrl,
            ),
        )
    }
    val initialPage = viewerAssets.indexOfFirst { it.id == activeAssetId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { viewerAssets.size }

    LaunchedEffect(viewerAssets, activeAssetId) {
        val targetPage = viewerAssets.indexOfFirst { it.id == activeAssetId }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState, viewerAssets, activeAssetId) {
        snapshotFlow { pagerState.isScrollInProgress }
            .drop(1)
            .filter { scrolling -> !scrolling }
            .collect {
                viewerAssets.getOrNull(pagerState.currentPage)?.id
                    ?.takeIf { it != activeAssetId }
                    ?.let(onAssetChanged)
            }
    }

    HorizontalPager(
        state = pagerState,
        key = { page -> viewerAssets[page].id },
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val pageAsset = viewerAssets[page]
        val hasSeedImage = pageAsset.previewUrl != null || pageAsset.thumbnailUrl != null
        if (
            pageAsset.id == activeAssetId &&
            (state.previewUrl != null || (state.asset != null && !hasSeedImage))
        ) {
            MediaDetailViewer(
                state = state,
                fallbackAsset = pageAsset,
                onPreviewError = onPreviewError,
                onPreviewLoaded = onPreviewLoaded,
                onRetryPreview = onRetryPreview,
            )
        } else {
            ViewerAssetPreview(pageAsset)
        }
    }
}

@Composable
private fun ViewerAssetPreview(asset: ViewerAssetSummary) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val imageUrl = asset.previewUrl ?: asset.thumbnailUrl
        if (imageUrl != null) {
            val variant = if (asset.previewUrl != null) {
                CloudImageVariant.PREVIEW
            } else {
                CloudImageVariant.THUMBNAIL
            }
            AsyncImage(
                model = cloudImageRequest(context, asset.id, variant, imageUrl),
                contentDescription = asset.originalFilename,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
        if (asset.mediaType == "video") {
            Text(
                text = "Video",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun MediaDetailViewer(
    state: AssetDetailUiState,
    fallbackAsset: ViewerAssetSummary,
    onPreviewError: () -> Unit,
    onPreviewLoaded: () -> Unit,
    onRetryPreview: () -> Unit,
) {
    val asset = state.asset
    val assetId = asset?.id ?: state.activeAssetId ?: return
    val mediaType = asset?.mediaType ?: fallbackAsset.mediaType
    if (mediaType == "video") {
        VideoDetailViewer(
            assetId = assetId,
            imageUrl = state.previewUrl,
            isRefreshingUrl = state.isRefreshingUrl,
            loadFailed = state.previewLoadFailed,
            onImageLoadFailed = onPreviewError,
            onImageLoaded = onPreviewLoaded,
            onRetry = onRetryPreview,
        )
        return
    }

    state.originalFilePath?.let { filePath ->
        FullResolutionImageViewer(
            filePath = filePath,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val imageUrl = state.previewUrl
    if (imageUrl == null) {
        MissingPhotoState(
            refreshing = state.isRefreshingUrl,
            failed = state.previewLoadFailed,
            onRetry = onRetryPreview,
        )
    } else {
        ZoomablePhotoViewer(
            imageUrl = imageUrl,
            contentDescription = asset?.originalFilename ?: fallbackAsset.originalFilename,
            assetKey = assetId,
            isRefreshingUrl = state.isRefreshingUrl,
            onImageLoadFailed = onPreviewError,
            onImageLoaded = onPreviewLoaded,
            onRetry = onRetryPreview,
        )
    }
}

@Composable
private fun VideoDetailViewer(
    assetId: String,
    imageUrl: String?,
    isRefreshingUrl: Boolean,
    loadFailed: Boolean,
    onImageLoadFailed: () -> Unit,
    onImageLoaded: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        imageUrl?.let { url ->
            AsyncImage(
                model = cloudImageRequest(context, assetId, CloudImageVariant.PREVIEW, url),
                contentDescription = "Video preview",
                contentScale = ContentScale.Fit,
                onError = { onImageLoadFailed() },
                onSuccess = { onImageLoaded() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            "Video playback is not available yet",
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        if (isRefreshingUrl) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        if (loadFailed && !isRefreshingUrl) {
            Button(onClick = onRetry, modifier = Modifier.align(Alignment.Center)) { Text("Retry") }
        }
    }
}

@Composable
private fun MissingPhotoState(
    refreshing: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            refreshing -> CircularProgressIndicator(color = Color.White)
            failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Cannot load photo", color = Color.White)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun DetailActionsMenu(
    state: AssetDetailUiState,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onAddToAlbum: () -> Unit,
    onLoadOriginal: () -> Unit,
    onUsePreview: () -> Unit,
    onDownloadOriginal: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val asset = state.asset
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = asset != null && !state.actionInProgress,
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Asset actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (asset?.mediaType == "image") {
                DropdownMenuItem(
                    text = {
                        Text(if (state.originalFilePath != null) "Use preview" else "Load original")
                    },
                    leadingIcon = { Icon(Icons.Outlined.HighQuality, contentDescription = null) },
                    enabled = state.originalStatus != OriginalImageStatus.LOADING &&
                        state.originalStatus != OriginalImageStatus.DOWNLOADING,
                    onClick = {
                        expanded = false
                        if (state.originalFilePath != null) onUsePreview() else onLoadOriginal()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Download original") },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    enabled = state.originalStatus != OriginalImageStatus.LOADING &&
                        state.originalStatus != OriginalImageStatus.DOWNLOADING,
                    onClick = {
                        expanded = false
                        onDownloadOriginal()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Add to album") },
                leadingIcon = { Icon(Icons.Outlined.PhotoAlbum, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddToAlbum()
                },
            )
            DropdownMenuItem(
                text = { Text(if (asset?.isArchived == true) "Unarchive" else "Archive") },
                leadingIcon = {
                    Icon(
                        if (asset?.isArchived == true) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onArchive()
                },
            )
            DropdownMenuItem(
                text = { Text(if (asset?.isTrashed == true) "Restore" else "Move to trash") },
                leadingIcon = {
                    Icon(
                        if (asset?.isTrashed == true) Icons.Outlined.Restore else Icons.Outlined.Delete,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    if (asset?.isTrashed == true) onRestore() else onTrash()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete permanently") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

private fun downloadFilename(originalFilename: String?): String {
    val sanitized = originalFilename
        ?.replace(Regex("[\\/:*?\"<>|]"), "_")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return sanitized ?: "photo-original"
}

private fun copyDetailValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun openAssetLocation(context: Context, latitude: Double, longitude: Double) {
    val coordinates = "$latitude,$longitude"
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:$coordinates?q=$coordinates"),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No maps app available", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "No maps app available", Toast.LENGTH_SHORT).show()
    }
}
