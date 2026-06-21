package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.repository.SyncRepository
import com.photomap.app.data.cache.OfflineImageCacheStatus
import com.photomap.app.data.preferences.SyncSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SyncRepository) : ViewModel() {
    val pendingCount: StateFlow<Int> = repository.pendingCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )
    val failedCount: StateFlow<Int> = repository.failedCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )
    val uploadingCount: StateFlow<Int> = repository.uploadingCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )
    val uploadedCount: StateFlow<Int> = repository.uploadedCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )
    val maxParallelUploads: StateFlow<Int> = repository.maxParallelUploads
    val uploadsPaused: StateFlow<Boolean> = repository.uploadsPaused
    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
    val wifiOnly: StateFlow<Boolean> = repository.wifiOnly
    val includeVideos: StateFlow<Boolean> = repository.includeVideos
    val offlineImageCacheEnabled: StateFlow<Boolean> = repository.offlineImageCacheEnabled
    val imageCacheLimitMb: StateFlow<Int> = repository.imageCacheLimitMb
    val offlineImageCacheStatus: StateFlow<OfflineImageCacheStatus> =
        repository.offlineImageCacheStatus ?: MutableStateFlow(OfflineImageCacheStatus())
    val imageCacheLimitPresetsMb: List<Int> = SyncSettingsStore.IMAGE_CACHE_LIMIT_PRESETS_MB
    val parallelUploadPresets: List<Int> = SyncSettingsStore.PARALLEL_UPLOAD_PRESETS

    fun sync() {
        viewModelScope.launch { repository.scanAndSync() }
    }

    fun retryFailed() {
        viewModelScope.launch { repository.retryFailed() }
    }

    fun setMaxParallelUploads(value: Int) {
        repository.setMaxParallelUploads(value)
    }

    fun setUploadsPaused(paused: Boolean) {
        repository.setUploadsPaused(paused)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        repository.setBackgroundSyncEnabled(enabled)
    }

    fun setWifiOnly(enabled: Boolean) {
        repository.setWifiOnly(enabled)
    }

    fun setIncludeVideos(enabled: Boolean) {
        viewModelScope.launch { repository.setIncludeVideos(enabled) }
    }

    fun setOfflineImageCacheEnabled(enabled: Boolean) {
        repository.setOfflineImageCacheEnabled(enabled)
    }

    fun setImageCacheLimitMb(value: Int) {
        viewModelScope.launch { repository.setImageCacheLimitMb(value) }
    }

    fun downloadOfflineImages() {
        repository.downloadOfflineImages()
    }

    fun clearOfflineImageCache() {
        viewModelScope.launch { repository.clearOfflineImageCache() }
    }
}

class SettingsViewModelFactory(
    private val repository: SyncRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(repository) as T
}
