package com.photomap.app.data.cache

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.photomap.app.data.preferences.SyncSettingsStore
import com.photomap.app.worker.OfflineImageCacheWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class OfflineImageCacheStatus(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val cacheSizeBytes: Long = 0,
    val errorMessage: String? = null,
)

class OfflineImageCacheCoordinator(
    context: Context,
    private val settingsStore: SyncSettingsStore,
    private val cacheManager: ImageCacheManager,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(OfflineImageCacheStatus())
    val status: StateFlow<OfflineImageCacheStatus> = _status.asStateFlow()
    val enabled: StateFlow<Boolean> = settingsStore.offlineImageCacheEnabled
    val limitMb: StateFlow<Int> = settingsStore.imageCacheLimitMb

    init {
        scope.launch { refreshUsage() }
    }

    fun restore(isLoggedIn: Boolean) {
        if (isLoggedIn && settingsStore.isOfflineImageCacheEnabled()) enqueue()
    }

    fun enqueue() {
        if (!settingsStore.isOfflineImageCacheEnabled()) return
        val request = OneTimeWorkRequestBuilder<OfflineImageCacheWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
        _status.value = _status.value.copy(running = false)
    }

    fun reschedule() {
        scope.launch {
            cancelAndWait()
            enqueue()
        }
    }

    fun setEnabled(value: Boolean) {
        settingsStore.setOfflineImageCacheEnabled(value)
        if (value) enqueue() else cancel()
    }

    suspend fun setLimitMb(value: Int) {
        cancelAndWait()
        withContext(Dispatchers.IO) { cacheManager.reconfigure(value) }
        refreshUsage()
        if (settingsStore.isOfflineImageCacheEnabled()) enqueue()
    }

    suspend fun clearAndDisable() {
        settingsStore.setOfflineImageCacheEnabled(false)
        cancelAndWait()
        withContext(Dispatchers.IO) { cacheManager.clear() }
        _status.value = OfflineImageCacheStatus()
    }

    suspend fun clearForAccountChange() {
        cancelAndWait()
        withContext(Dispatchers.IO) { cacheManager.clear() }
        _status.value = OfflineImageCacheStatus()
    }

    suspend fun refreshUsage() {
        val size = withContext(Dispatchers.IO) { cacheManager.cacheSizeBytes() }
        _status.value = _status.value.copy(cacheSizeBytes = size)
    }

    fun updateProgress(completed: Int, total: Int) {
        _status.value = _status.value.copy(
            running = true,
            completed = completed,
            total = total,
            errorMessage = null,
        )
    }

    fun stopped() {
        _status.value = _status.value.copy(running = false)
    }

    suspend fun complete() {
        refreshUsage()
        _status.value = _status.value.copy(running = false, errorMessage = null)
    }

    suspend fun fail(message: String) {
        refreshUsage()
        _status.value = _status.value.copy(running = false, errorMessage = message)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (settingsStore.isWifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED,
        )
        .build()

    private suspend fun cancelAndWait() {
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(WORK_NAME).result.get()
        }
        _status.value = _status.value.copy(running = false)
    }

    companion object {
        const val WORK_NAME = "offline-image-cache"
        private const val BACKOFF_SECONDS = 30L
    }
}
