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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.network.AlbumDto
import com.photomap.app.ui.AlbumDetailUiState
import com.photomap.app.ui.AlbumEditorState
import com.photomap.app.ui.AlbumsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (AlbumDto) -> Unit,
    onDelete: (AlbumDto) -> Unit,
    onSave: (String, String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onCreate) { Icon(Icons.Outlined.Add, contentDescription = "Create album") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.albums, key = AlbumDto::id) { album ->
                    ListItem(
                        headlineContent = { Text(album.name) },
                        supportingContent = { album.description?.let { Text(it) } },
                        trailingContent = { AlbumMenu(album, onEdit, onDelete) },
                        modifier = Modifier.clickable { onAlbum(album.id) },
                    )
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (!state.loading && state.albums.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "No albums")
                    if (state.error != null) Button(onClick = onRetry) { Text("Retry") }
                }
            }
            state.error?.takeIf { state.albums.isNotEmpty() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    state.editor?.let { AlbumEditorDialog(it, onSave, onDismissEditor) }
    state.deleteCandidate?.let { album ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete album?") },
            text = { Text("Delete '${album.name}'? Assets in the album will not be deleted.") },
            confirmButton = { Button(onClick = onConfirmDelete) { Text("Delete album") } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AlbumEditorDialog(
    editor: AlbumEditorState,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(editor) { mutableStateOf(editor.name) }
    var description by remember(editor) { mutableStateOf(editor.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.albumId == null) "Create album" else "Edit album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, description) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AlbumMenu(album: AlbumDto, onEdit: (AlbumDto) -> Unit, onDelete: (AlbumDto) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Album actions") }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit(album) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { expanded = false; onDelete(album) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    onBack: () -> Unit,
    onAsset: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.album?.name ?: "Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.assets, key = AssetUiModel::id) { asset ->
                    AlbumAssetTile(asset, onAsset, onRemove)
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (!state.loading && state.assets.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "No assets in this album")
                    if (state.error != null) Button(onClick = onRetry) { Text("Retry") }
                }
            }
            state.error?.takeIf { state.assets.isNotEmpty() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun AlbumAssetTile(asset: AssetUiModel, onAsset: (String) -> Unit, onRemove: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(asset.aspectRatio.coerceIn(0.65f, 1.8f))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onAsset(asset.id) },
    ) {
        if (asset.thumbnailUrl == null) {
            Icon(Icons.Outlined.BrokenImage, contentDescription = null, modifier = Modifier.align(Alignment.Center))
        } else {
            AsyncImage(
                model = asset.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Asset actions")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Remove from album") },
                    onClick = {
                        expanded = false
                        onRemove(asset.id)
                    },
                )
            }
        }
    }
}
