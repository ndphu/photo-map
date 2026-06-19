package com.photomap.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pendingCount: Int,
    failedCount: Int,
    uploadingCount: Int,
    uploadedCount: Int,
    maxParallelUploads: Int,
    backgroundSyncEnabled: Boolean,
    wifiOnly: Boolean,
    includeVideos: Boolean,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onRetry: () -> Unit,
    onMaxParallelUploadsChange: (Int) -> Unit,
    onBackgroundSyncChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onIncludeVideosChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
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
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pending uploads")
                Text(pendingCount.toString())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Failed uploads")
                Text(failedCount.toString())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Uploading")
                Text(uploadingCount.toString())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Uploaded")
                Text(uploadedCount.toString())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Background sync")
                Switch(
                    checked = backgroundSyncEnabled,
                    onCheckedChange = onBackgroundSyncChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wi-Fi only")
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Include videos")
                Switch(
                    checked = includeVideos,
                    onCheckedChange = onIncludeVideosChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Parallel uploads")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onMaxParallelUploadsChange(maxParallelUploads - 1) },
                        enabled = maxParallelUploads > MIN_PARALLEL_UPLOADS,
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Decrease parallel uploads")
                    }
                    Text(maxParallelUploads.toString())
                    IconButton(
                        onClick = { onMaxParallelUploadsChange(maxParallelUploads + 1) },
                        enabled = maxParallelUploads < MAX_PARALLEL_UPLOADS,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Increase parallel uploads")
                    }
                }
            }
            Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                Text("Scan and sync now")
            }
            OutlinedButton(
                onClick = onRetry,
                enabled = failedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retry failed uploads")
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        }
    }
}

private const val MIN_PARALLEL_UPLOADS = 1
private const val MAX_PARALLEL_UPLOADS = 16
