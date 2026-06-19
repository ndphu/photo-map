package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.repository.SyncRepository
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
    val maxParallelUploads: StateFlow<Int> = repository.maxParallelUploads
    val backgroundSyncEnabled: StateFlow<Boolean> = repository.backgroundSyncEnabled
    val wifiOnly: StateFlow<Boolean> = repository.wifiOnly

    fun sync() {
        viewModelScope.launch { repository.scanAndSync() }
    }

    fun retryFailed() {
        viewModelScope.launch { repository.retryFailed() }
    }

    fun setMaxParallelUploads(value: Int) {
        repository.setMaxParallelUploads(value)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        repository.setBackgroundSyncEnabled(enabled)
    }

    fun setWifiOnly(enabled: Boolean) {
        repository.setWifiOnly(enabled)
    }
}

class SettingsViewModelFactory(
    private val repository: SyncRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(repository) as T
}
