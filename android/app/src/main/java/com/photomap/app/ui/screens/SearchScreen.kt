package com.photomap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.photomap.app.data.cache.cloudImageRequest
import com.photomap.app.data.cache.cloudImageVariant
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.ui.SearchUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    assets: LazyPagingItems<AssetUiModel>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onAsset: (AssetUiModel) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                placeholder = { Text("Search photos and videos") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
            SearchResults(state, assets, onAsset, onRetry, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    assets: LazyPagingItems<AssetUiModel>,
    onAsset: (AssetUiModel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val refresh = assets.loadState.refresh
    when {
        state.query.isBlank() -> SearchMessage("Search your gallery", "Enter a label, caption, or text.", modifier)
        state.submittedQuery.isBlank() -> SearchMessage("Searching...", "", modifier)
        state.error != null && assets.itemCount == 0 -> SearchMessage(
            "Cannot search right now",
            "Check your connection and retry.",
            modifier,
            onRetry,
        )
        refresh is LoadState.Loading && assets.itemCount == 0 -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        refresh is LoadState.Error && assets.itemCount == 0 -> SearchMessage(
            "Cannot search right now",
            "Check your connection and retry.",
            modifier,
        ) {
            assets.retry()
        }
        assets.itemCount == 0 && refresh is LoadState.NotLoading -> SearchMessage("No results", "Try another search.", modifier)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(count = assets.itemCount, key = assets.itemKey(AssetUiModel::id)) { index ->
                assets[index]?.let { SearchTile(it, onAsset) }
            }
            if (assets.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (assets.loadState.append is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = assets::retry, modifier = Modifier.padding(16.dp)) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun SearchTile(asset: AssetUiModel, onAsset: (AssetUiModel) -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(asset.aspectRatio.coerceIn(0.65f, 1.8f))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onAsset(asset) },
    ) {
        if (asset.thumbnailUrl == null) {
            Icon(Icons.Outlined.BrokenImage, contentDescription = null, modifier = Modifier.align(Alignment.Center))
        } else {
            AsyncImage(
                model = cloudImageRequest(
                    context,
                    asset.id,
                    cloudImageVariant(asset.galleryImageVariant),
                    requireNotNull(asset.thumbnailUrl),
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchMessage(
    title: String,
    message: String,
    modifier: Modifier,
    action: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message.isNotEmpty()) Text(message, modifier = Modifier.padding(top = 8.dp))
        if (action != null) Button(onClick = action, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
    }
}
