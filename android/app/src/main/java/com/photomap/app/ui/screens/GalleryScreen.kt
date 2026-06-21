package com.photomap.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.cache.cloudImageRequest
import com.photomap.app.data.cache.cloudImageVariant
import com.photomap.app.data.gallery.GalleryFilter
import com.photomap.app.data.gallery.GalleryGridZoomAction
import com.photomap.app.data.gallery.GalleryListItem
import com.photomap.app.data.gallery.GalleryMediaType
import com.photomap.app.data.gallery.GalleryQuickFilter
import com.photomap.app.data.gallery.GallerySection
import com.photomap.app.data.gallery.galleryGridZoomAction
import com.photomap.app.data.gallery.quickFilter
import com.photomap.app.data.gallery.section
import com.photomap.app.data.network.NetworkState
import com.photomap.app.data.preferences.MAX_GALLERY_COLUMNS
import com.photomap.app.data.preferences.MIN_GALLERY_COLUMNS
import com.photomap.app.ui.GalleryInteractionState
import com.photomap.app.ui.GallerySyncSummary
import com.photomap.app.ui.GalleryUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    assets: LazyPagingItems<GalleryListItem>,
    state: GalleryUiState,
    columnCount: Int,
    onIncreaseColumns: () -> Unit,
    onDecreaseColumns: () -> Unit,
    onAssetTap: (AssetUiModel) -> Unit,
    onAssetLongPress: (String) -> Unit,
    onThumbnailError: (String, String, String, Throwable) -> Unit,
    onCloseSelection: () -> Unit,
    onFavoriteSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onAddSelectedToAlbum: () -> Unit,
    onRetryBatch: () -> Unit,
    onDismissResult: () -> Unit,
    onDismissAlbumPicker: () -> Unit,
    onAlbumSelected: (String) -> Unit,
    onStartSync: () -> Unit,
    onRetryFailedUploads: () -> Unit,
    onClearFilter: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onAlbums: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onQuickFilter: (GalleryQuickFilter) -> Unit,
    onSection: (GallerySection) -> Unit,
) {
    val isRefreshing = state.metadataSync.isSyncing && assets.itemCount > 0
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var previousFilter by remember { mutableStateOf(state.filter) }
    var pendingGridRestore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var densityFeedback by remember { mutableStateOf<String?>(null) }
    val showJumpToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > JUMP_TO_TOP_THRESHOLD }
    }

    LaunchedEffect(state.filter) {
        if (previousFilter != state.filter) {
            gridState.scrollToItem(0)
            previousFilter = state.filter
        }
    }

    LaunchedEffect(columnCount) {
        pendingGridRestore?.let { (index, offset) ->
            gridState.scrollToItem(index, offset)
            pendingGridRestore = null
            densityFeedback = "$columnCount per row"
            delay(DENSITY_FEEDBACK_DURATION_MILLIS)
            densityFeedback = null
        }
    }

    val onGridZoom: (GalleryGridZoomAction) -> Unit = { action ->
        val canChange = when (action) {
            GalleryGridZoomAction.DECREASE_COLUMNS -> columnCount > MIN_GALLERY_COLUMNS
            GalleryGridZoomAction.INCREASE_COLUMNS -> columnCount < MAX_GALLERY_COLUMNS
        }
        if (canChange) {
            pendingGridRestore = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            when (action) {
                GalleryGridZoomAction.DECREASE_COLUMNS -> onDecreaseColumns()
                GalleryGridZoomAction.INCREASE_COLUMNS -> onIncreaseColumns()
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.interaction.selectedIds.isNotEmpty()) {
                SelectionTopBar(
                    interaction = state.interaction,
                    onClose = onCloseSelection,
                    onFavorite = onFavoriteSelected,
                    onArchive = onArchiveSelected,
                    onTrash = onTrashSelected,
                    onAddToAlbum = onAddSelectedToAlbum,
                )
            } else {
                GalleryTopBar(
                    section = state.filter.section(),
                    onSearch = onSearch,
                    onAlbums = onAlbums,
                    onSettings = onSettings,
                    onSection = onSection,
                )
            }
        },
        floatingActionButton = {
            if (showJumpToTop) {
                FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Jump to top")
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.networkState == NetworkState.OFFLINE) OfflineBanner()
                SyncStatusBanner(state.sync, onStartSync, onRetryFailedUploads)
                BatchStatusBanner(state.interaction, onRetryBatch, onDismissResult)
                if (state.metadataSync.errorMessage != null && assets.itemCount > 0) {
                    MetadataSyncErrorBanner(state.metadataSync.errorMessage, onRetry)
                }
                QuickFilters(state.filter.quickFilter(), onQuickFilter)
                GalleryContent(
                    assets = assets,
                    gridState = gridState,
                    columnCount = columnCount,
                    filter = state.filter,
                    networkState = state.networkState,
                    selectedIds = state.interaction.selectedIds,
                    metadataSyncing = state.metadataSync.isSyncing,
                    metadataError = state.metadataSync.errorMessage,
                    onAssetTap = onAssetTap,
                    onAssetLongPress = onAssetLongPress,
                    onThumbnailError = onThumbnailError,
                    onRetry = onRetry,
                    onStartSync = onStartSync,
                    onClearFilter = onClearFilter,
                    onGridZoom = onGridZoom,
                    modifier = Modifier.weight(1f),
                )
            }
            densityFeedback?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 3.dp,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    if (state.interaction.showAlbumPicker) {
        AlertDialog(
            onDismissRequest = onDismissAlbumPicker,
            title = { Text("Add selected to album") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.interaction.albums.isEmpty()) {
                        Text("No albums yet. Create one from the Albums screen.")
                    } else {
                        state.interaction.albums.forEach { album ->
                            TextButton(
                                onClick = { onAlbumSelected(album.id) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    section: GallerySection,
    onSearch: () -> Unit,
    onAlbums: () -> Unit,
    onSettings: () -> Unit,
    onSection: (GallerySection) -> Unit,
) {
    TopAppBar(
        title = { Text(section.title) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
            IconButton(onClick = onAlbums) { Icon(Icons.Outlined.PhotoAlbum, contentDescription = "Albums") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            SectionMenu(current = section, onSection = onSection)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    interaction: GalleryInteractionState,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onAddToAlbum: () -> Unit,
) {
    val enabled = interaction.batchProgress == null && !interaction.albumPickerLoading
    TopAppBar(
        title = { Text("${interaction.selectedIds.size} selected") },
        navigationIcon = {
            IconButton(onClick = onClose, enabled = enabled) {
                Icon(Icons.Outlined.Close, contentDescription = "Close selection")
            }
        },
        actions = {
            IconButton(onClick = onFavorite, enabled = enabled) {
                Icon(Icons.Filled.Favorite, contentDescription = "Favorite selected")
            }
            IconButton(onClick = onArchive, enabled = enabled) {
                Icon(Icons.Outlined.Archive, contentDescription = "Archive selected")
            }
            IconButton(onClick = onTrash, enabled = enabled) {
                Icon(Icons.Outlined.Delete, contentDescription = "Move selected to trash")
            }
            IconButton(onClick = onAddToAlbum, enabled = enabled) {
                Icon(Icons.Outlined.PhotoAlbum, contentDescription = "Add selected to album")
            }
        },
    )
}

@Composable
private fun SyncStatusBanner(
    sync: GallerySyncSummary,
    onStartSync: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    if (sync.pending == 0 && sync.uploading == 0 && sync.failed == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${sync.pending} pending | ${sync.uploading} uploading | ${sync.failed} failed",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (sync.failed > 0) {
                TextButton(onClick = onRetryFailed) { Text("Retry") }
            } else if (sync.uploading == 0 && sync.pending > 0) {
                TextButton(onClick = onStartSync) { Text("Start sync") }
            }
        }
    }
}

@Composable
private fun BatchStatusBanner(
    interaction: GalleryInteractionState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    interaction.batchProgress?.let { progress ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${progress.label} ${progress.completed} / ${progress.total}...", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { progress.completed.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    interaction.resultMessage?.let { message ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (interaction.canRetryBatch) TextButton(onClick = onRetry) { Text("Retry") }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
            }
        }
    }
    if (interaction.albumPickerLoading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun GalleryContent(
    assets: LazyPagingItems<GalleryListItem>,
    gridState: LazyGridState,
    columnCount: Int,
    filter: GalleryFilter,
    networkState: NetworkState,
    selectedIds: Set<String>,
    metadataSyncing: Boolean,
    metadataError: String?,
    onAssetTap: (AssetUiModel) -> Unit,
    onAssetLongPress: (String) -> Unit,
    onThumbnailError: (String, String, String, Throwable) -> Unit,
    onRetry: () -> Unit,
    onStartSync: () -> Unit,
    onClearFilter: () -> Unit,
    onGridZoom: (GalleryGridZoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshState = assets.loadState.refresh
    when {
        metadataSyncing && assets.itemCount == 0 -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        metadataError != null && assets.itemCount == 0 -> FullScreenMessage(
            title = if (networkState == NetworkState.OFFLINE) "You're offline" else "Unable to load gallery",
            message = metadataError,
            actionLabel = "Retry",
            onAction = onRetry,
            modifier = modifier,
        )

        assets.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            val empty = emptyState(filter, networkState)
            FullScreenMessage(
                title = empty.title,
                message = empty.message,
                actionLabel = empty.actionLabel,
                onAction = when (empty.action) {
                    EmptyAction.CLEAR_FILTER -> onClearFilter
                    EmptyAction.RETRY -> onRetry
                    EmptyAction.START_SYNC -> onStartSync
                },
                modifier = modifier,
            )
        }

        else -> GalleryGrid(
            assets = assets,
            gridState = gridState,
            columnCount = columnCount,
            selectedIds = selectedIds,
            onAssetTap = onAssetTap,
            onAssetLongPress = onAssetLongPress,
            onThumbnailError = onThumbnailError,
            onRetry = onRetry,
            onGridZoom = onGridZoom,
            modifier = modifier,
        )
    }
}

@Composable
private fun MetadataSyncErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
    assets: LazyPagingItems<GalleryListItem>,
    gridState: LazyGridState,
    columnCount: Int,
    selectedIds: Set<String>,
    onAssetTap: (AssetUiModel) -> Unit,
    onAssetLongPress: (String) -> Unit,
    onThumbnailError: (String, String, String, Throwable) -> Unit,
    onRetry: () -> Unit,
    onGridZoom: (GalleryGridZoomAction) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier = modifier.galleryPinchResize(onGridZoom),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = assets.itemCount,
            key = assets.itemKey(GalleryListItem::stableKey),
            span = { index ->
                if (assets.peek(index) is GalleryListItem.DateHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            },
        ) { index ->
            when (val item = assets[index]) {
                is GalleryListItem.Asset -> GalleryTile(
                    asset = item.value,
                    selected = item.value.id in selectedIds,
                    onTap = onAssetTap,
                    onLongPress = onAssetLongPress,
                    onThumbnailError = onThumbnailError,
                )
                is GalleryListItem.DateHeader -> Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
                null -> Unit
            }
        }

        when (val appendState = assets.loadState.append) {
            is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(safeErrorMessage(appendState.error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

private fun Modifier.galleryPinchResize(
    onZoomAction: (GalleryGridZoomAction) -> Unit,
): Modifier = pointerInput(onZoomAction) {
    awaitEachGesture {
        var accumulatedZoom = 1f
        var pointersPressed = true
        while (pointersPressed) {
            val event = awaitPointerEvent()
            val pressedPointers = event.changes.count { it.pressed }
            if (pressedPointers >= 2) {
                accumulatedZoom *= event.calculateZoom()
                event.changes.forEach { change ->
                    if (change.pressed) change.consume()
                }
                galleryGridZoomAction(accumulatedZoom)?.let { action ->
                    onZoomAction(action)
                    accumulatedZoom = 1f
                }
            }
            pointersPressed = event.changes.any { it.pressed }
        }
    }
}

@Composable
private fun QuickFilters(selected: GalleryQuickFilter, onSelected: (GalleryQuickFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryQuickFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun SectionMenu(current: GallerySection, onSection: (GallerySection) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Gallery views")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GallerySection.entries.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.title) },
                    leadingIcon = { Icon(section.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        if (section != current) onSection(section)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    asset: AssetUiModel,
    selected: Boolean,
    onTap: (AssetUiModel) -> Unit,
    onLongPress: (String) -> Unit,
    onThumbnailError: (String, String, String, Throwable) -> Unit,
) {
    val context = LocalContext.current
    val selectionColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(asset.aspectRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(SELECTION_BORDER_WIDTH, selectionColor)
            .combinedClickable(
                onClick = { onTap(asset) },
                onLongClick = { onLongPress(asset.id) },
            ),
    ) {
        val imageUrl = asset.thumbnailUrl
        if (imageUrl != null) {
            AsyncImage(
                model = cloudImageRequest(
                    context,
                    asset.id,
                    cloudImageVariant(asset.galleryImageVariant),
                    imageUrl,
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { state ->
                    onThumbnailError(
                        asset.id,
                        asset.galleryImageVariant,
                        imageUrl,
                        state.result.throwable,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        } else if (asset.isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        if (asset.isVideo) {
            Text(
                text = formatDuration(asset.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = "Offline - showing loaded items",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FullScreenMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

private data class EmptyState(
    val title: String,
    val message: String,
    val actionLabel: String,
    val action: EmptyAction,
)

private enum class EmptyAction {
    START_SYNC,
    CLEAR_FILTER,
    RETRY,
}

private fun emptyState(filter: GalleryFilter, networkState: NetworkState): EmptyState = when {
    networkState == NetworkState.OFFLINE -> EmptyState(
        "You're offline",
        "Reconnect to load your cloud gallery.",
        "Retry",
        EmptyAction.RETRY,
    )
    filter.trashed -> EmptyState(
        "Trash is empty",
        "Cloud assets moved to trash appear here.",
        "Clear filter",
        EmptyAction.CLEAR_FILTER,
    )
    filter.archived == true -> EmptyState(
        "No archived assets",
        "Archived photos and videos appear here.",
        "Clear filter",
        EmptyAction.CLEAR_FILTER,
    )
    filter.favoriteOnly -> EmptyState(
        "No favorites yet",
        "Favorite an asset to keep it easy to find.",
        "Clear filter",
        EmptyAction.CLEAR_FILTER,
    )
    filter.mediaType == GalleryMediaType.VIDEO -> EmptyState(
        "No videos yet",
        "Sync local videos to your cloud gallery.",
        "Start sync",
        EmptyAction.START_SYNC,
    )
    filter.mediaType == GalleryMediaType.IMAGE -> EmptyState(
        "No photos yet",
        "Sync local photos to your cloud gallery.",
        "Start sync",
        EmptyAction.START_SYNC,
    )
    else -> EmptyState(
        "No photos yet",
        "Start sync to upload local photos and videos.",
        "Start sync",
        EmptyAction.START_SYNC,
    )
}

private fun formatDuration(durationMs: Long?): String {
    val totalSeconds = ((durationMs ?: 0L) / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun safeErrorMessage(error: Throwable): String = when (error) {
    is com.photomap.app.data.gallery.UnauthorizedGalleryException -> "Session expired"
    is java.io.IOException -> "Network unavailable"
    is retrofit2.HttpException -> if (error.code() >= 500) "Server unavailable" else "Unable to load gallery"
    else -> "Unable to load gallery"
}

private val GalleryQuickFilter.label: String
    get() = when (this) {
        GalleryQuickFilter.ALL -> "All"
        GalleryQuickFilter.PHOTOS -> "Photos"
        GalleryQuickFilter.VIDEOS -> "Videos"
        GalleryQuickFilter.FAVORITES -> "Favorites"
    }

private val GallerySection.title: String
    get() = when (this) {
        GallerySection.MAIN -> "Gallery"
        GallerySection.ARCHIVE -> "Archive"
        GallerySection.TRASH -> "Trash"
    }

private val GallerySection.icon
    get() = when (this) {
        GallerySection.MAIN -> Icons.Outlined.PhotoLibrary
        GallerySection.ARCHIVE -> Icons.Outlined.Archive
        GallerySection.TRASH -> Icons.Outlined.Delete
    }

private const val JUMP_TO_TOP_THRESHOLD = 12
private const val DENSITY_FEEDBACK_DURATION_MILLIS = 1_000L
private val SELECTION_BORDER_WIDTH = 3.dp
private const val MIN_ASPECT_RATIO = 0.65f
private const val MAX_ASPECT_RATIO = 1.8f
