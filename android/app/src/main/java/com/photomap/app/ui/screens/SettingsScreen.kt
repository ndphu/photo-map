package com.photomap.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photomap.app.data.cache.OfflineImageCacheStatus
import com.photomap.app.data.preferences.BackendUrlConfiguration
import com.photomap.app.data.repository.AssetMetadataBackfillState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pendingCount: Int,
    failedCount: Int,
    uploadingCount: Int,
    uploadedCount: Int,
    maxParallelUploads: Int,
    uploadsPaused: Boolean,
    parallelUploadPresets: List<Int>,
    backgroundSyncEnabled: Boolean,
    wifiOnly: Boolean,
    includeVideos: Boolean,
    offlineImageCacheEnabled: Boolean,
    imageCacheLimitMb: Int,
    imageCacheLimitPresetsMb: List<Int>,
    imageCacheStatus: OfflineImageCacheStatus,
    backendConfiguration: BackendUrlConfiguration,
    backendSaving: Boolean,
    backendError: String?,
    metadataBackfillState: AssetMetadataBackfillState,
    metadataBackfillPendingCount: Int,
    metadataBackfillFailedCount: Int,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onRetry: () -> Unit,
    onMaxParallelUploadsChange: (Int) -> Unit,
    onUploadsPausedChange: (Boolean) -> Unit,
    onBackgroundSyncChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onIncludeVideosChange: (Boolean) -> Unit,
    onOfflineImageCacheEnabledChange: (Boolean) -> Unit,
    onImageCacheLimitChange: (Int) -> Unit,
    onDownloadOfflineImages: () -> Unit,
    onClearOfflineImageCache: () -> Unit,
    onRetryMetadataBackfill: () -> Unit,
    onMetadataPermissionGranted: () -> Unit,
    onConfigureBackend: (Boolean, String) -> Unit,
    onClearBackendError: () -> Unit,
    onLogout: () -> Unit,
) {
    var cacheLimitMenuExpanded by remember { mutableStateOf(false) }
    var parallelUploadsMenuExpanded by remember { mutableStateOf(false) }
    var showClearCacheConfirmation by remember { mutableStateOf(false) }
    var showBackendDialog by remember { mutableStateOf(false) }
    val metadataPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onMetadataPermissionGranted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionTitle("Backend")
            BackendUrlRow(
                url = backendConfiguration.effectiveBaseUrl,
                onClick = {
                    onClearBackendError()
                    showBackendDialog = true
                },
            )
            backendError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            SettingsSectionTitle("Cloud backup")
            SettingSwitchRow("Background sync", backgroundSyncEnabled, onBackgroundSyncChange)
            SettingsDivider()
            SettingSwitchRow("Wi-Fi only", wifiOnly, onWifiOnlyChange)

            SettingsSectionTitle("Upload queue")
            SettingSwitchRow("Pause uploads", uploadsPaused, onUploadsPausedChange)
            SettingsDivider()
            SyncCountRow("Pending", pendingCount)
            SettingsDivider()
            SyncCountRow("Uploading", uploadingCount)
            SettingsDivider()
            SyncCountRow("Failed", failedCount, error = failedCount > 0)
            SettingsDivider()
            SyncCountRow("Uploaded", uploadedCount)
            SettingsDivider()
            SettingSwitchRow("Include videos", includeVideos, onIncludeVideosChange)
            SettingsDivider()
            ParallelUploadsRow(
                maxParallelUploads = maxParallelUploads,
                presets = parallelUploadPresets,
                expanded = parallelUploadsMenuExpanded,
                onExpandedChange = { parallelUploadsMenuExpanded = it },
                onChange = onMaxParallelUploadsChange,
            )
            SettingsActionArea {
                Button(
                    onClick = onSync,
                    enabled = !uploadsPaused,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = null)
                    Text("Scan and sync now", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onRetry,
                    enabled = failedCount > 0 && !uploadsPaused,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Replay, contentDescription = null)
                    Text("Retry failed", modifier = Modifier.padding(start = 8.dp))
                }
            }

            SettingsSectionTitle("Offline images")
            SettingSwitchRow(
                label = "Offline image cache",
                checked = offlineImageCacheEnabled,
                onCheckedChange = onOfflineImageCacheEnabledChange,
            )
            SettingsDivider()
            CacheLimitRow(
                imageCacheLimitMb = imageCacheLimitMb,
                presetsMb = imageCacheLimitPresetsMb,
                enabled = offlineImageCacheEnabled,
                expanded = cacheLimitMenuExpanded,
                onExpandedChange = { cacheLimitMenuExpanded = it },
                onLimitChange = onImageCacheLimitChange,
            )
            SettingsDivider()
            SettingValueRow(
                label = "Storage used",
                value = formatCacheUsage(imageCacheStatus.cacheSizeBytes, imageCacheLimitMb),
            )
            CacheProgress(imageCacheStatus)
            SettingsActionArea {
                Button(
                    onClick = onDownloadOfflineImages,
                    enabled = offlineImageCacheEnabled && !imageCacheStatus.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Text("Download offline", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { showClearCacheConfirmation = true },
                    enabled = imageCacheStatus.cacheSizeBytes > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Text("Clear image cache", modifier = Modifier.padding(start = 8.dp))
                }
            }

            SettingsSectionTitle("Photo metadata")
            SyncCountRow("Pending", metadataBackfillPendingCount)
            SettingsDivider()
            SyncCountRow("Failed", metadataBackfillFailedCount, error = metadataBackfillFailedCount > 0)
            MetadataBackfillProgress(metadataBackfillState)
            SettingsActionArea {
                Button(
                    onClick = {
                        if (metadataBackfillState.needsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            metadataPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                        } else {
                            onRetryMetadataBackfill()
                        }
                    },
                    enabled = !metadataBackfillState.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = null)
                    Text(
                        if (metadataBackfillState.needsPermission) "Allow location metadata" else "Retry metadata update",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            SettingsSectionTitle("Account")
            SettingsActionArea(bottomPadding = 32.dp) {
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Logout, contentDescription = null)
                    Text("Sign out", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showClearCacheConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmation = false },
            title = { Text("Clear offline images?") },
            text = { Text("Cached thumbnails and previews will be removed and offline image cache will be turned off. Cloud assets will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirmation = false
                        onClearOfflineImageCache()
                    },
                ) { Text("Clear cache") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    if (showBackendDialog) {
        BackendServerDialog(
            configuration = backendConfiguration,
            saving = backendSaving,
            externalError = backendError,
            onDismiss = { showBackendDialog = false },
            onSave = { useCustomUrl, customBaseUrl ->
                showBackendDialog = false
                onConfigureBackend(useCustomUrl, customBaseUrl)
            },
        )
    }
}

@Composable
private fun BackendUrlRow(url: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("Server") },
        supportingContent = {
            Text(
                text = url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            TextButton(onClick = onClick) { Text("Change") }
        },
    )
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun SyncCountRow(label: String, count: Int, error: Boolean = false) {
    SettingValueRow(
        label = label,
        value = count.toString(),
        valueColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, color = valueColor) },
    )
}

@Composable
private fun ParallelUploadsRow(
    maxParallelUploads: Int,
    presets: List<Int>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChange: (Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text("Parallel uploads") },
        trailingContent = {
            Box {
                OutlinedButton(onClick = { onExpandedChange(true) }) {
                    Text(maxParallelUploads.toString())
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                ) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.toString()) },
                            onClick = {
                                onExpandedChange(false)
                                onChange(preset)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CacheLimitRow(
    imageCacheLimitMb: Int,
    presetsMb: List<Int>,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text("Cache limit") },
        trailingContent = {
            Box {
                OutlinedButton(
                    onClick = { onExpandedChange(true) },
                    enabled = enabled,
                ) {
                    Text(formatCacheLimit(imageCacheLimitMb))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                ) {
                    presetsMb.forEach { limitMb ->
                        DropdownMenuItem(
                            text = { Text(formatCacheLimit(limitMb)) },
                            onClick = {
                                onExpandedChange(false)
                                onLimitChange(limitMb)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CacheProgress(status: OfflineImageCacheStatus) {
    if (!status.running && status.errorMessage == null) return
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.running) {
            val total = status.total.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { status.completed.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${status.completed} of ${status.total} cached",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        status.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MetadataBackfillProgress(state: AssetMetadataBackfillState) {
    if (!state.running && state.errorMessage == null && !state.needsPermission) return
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.running) {
            val total = state.total.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { state.completed.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${state.completed} of ${state.total} updated", style = MaterialTheme.typography.bodySmall)
        }
        if (state.needsPermission) {
            Text("Location metadata permission is required for backfill.", style = MaterialTheme.typography.bodySmall)
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SettingsActionArea(
    bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
}

private const val BYTES_PER_MEGABYTE = 1024L * 1024L

private fun formatCacheLimit(limitMb: Int): String =
    if (limitMb >= 1024) "${limitMb / 1024} GB" else "$limitMb MB"

private fun formatCacheUsage(bytes: Long, limitMb: Int): String {
    val usedMb = bytes / BYTES_PER_MEGABYTE
    return "$usedMb MB / ${formatCacheLimit(limitMb)}"
}
