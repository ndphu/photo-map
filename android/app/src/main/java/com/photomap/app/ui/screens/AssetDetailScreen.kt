package com.photomap.app.ui.screens

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
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photomap.app.ui.AssetDetailUiState
import com.photomap.app.ui.viewer.ZoomablePhotoViewer

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
) {
    val asset = state.asset
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.originalFilename ?: "Asset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                asset?.let {
                    MediaDetailViewer(
                        state = state,
                        onPreviewError = onPreviewError,
                        onPreviewLoaded = onPreviewLoaded,
                        onRetryPreview = onRetryPreview,
                    )
                }
                if (state.loading || state.actionInProgress || (asset == null && state.isRefreshingUrl)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            asset?.let {
                Text(
                    listOfNotNull(
                        it.mediaType,
                        it.width?.let { width -> "${width}x${it.height ?: 0}" },
                        it.city,
                    ).joinToString(" | "),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
}

@Composable
private fun MediaDetailViewer(
    state: AssetDetailUiState,
    onPreviewError: () -> Unit,
    onPreviewLoaded: () -> Unit,
    onRetryPreview: () -> Unit,
) {
    val asset = state.asset ?: return
    if (asset.mediaType == "video") {
        VideoDetailViewer(
            imageUrl = state.previewUrl,
            isRefreshingUrl = state.isRefreshingUrl,
            loadFailed = state.previewLoadFailed,
            onImageLoadFailed = onPreviewError,
            onImageLoaded = onPreviewLoaded,
            onRetry = onRetryPreview,
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
            contentDescription = asset.originalFilename,
            assetKey = asset.id,
            isRefreshingUrl = state.isRefreshingUrl,
            onImageLoadFailed = onPreviewError,
            onImageLoaded = onPreviewLoaded,
            onRetry = onRetryPreview,
        )
    }
}

@Composable
private fun VideoDetailViewer(
    imageUrl: String?,
    isRefreshingUrl: Boolean,
    loadFailed: Boolean,
    onImageLoadFailed: () -> Unit,
    onImageLoaded: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        imageUrl?.let { url ->
            AsyncImage(
                model = url,
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
