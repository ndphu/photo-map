package com.photomap.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.local.SyncStatus
import com.photomap.app.data.media.MediaStoreScanner
import com.photomap.app.data.preferences.SyncSettingsStore
import com.photomap.app.data.cache.OfflineImageCacheCoordinator
import com.photomap.app.worker.MediaSyncWorker
import com.photomap.app.worker.PeriodicMediaScanWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit

interface GallerySyncController {
    val pendingCount: Flow<Int>
    val failedCount: Flow<Int>
    val uploadingCount: Flow<Int>
    val uploadedCount: Flow<Int>

    suspend fun scanAndSync()
    suspend fun retryFailed()
}

class SyncRepository(
    private val context: Context,
    private val scanner: MediaStoreScanner,
    private val localAssetDao: LocalAssetDao,
    private val settingsStore: SyncSettingsStore,
    private val assetMutationQueue: AssetMutationQueue? = null,
    private val offlineImageCache: OfflineImageCacheCoordinator? = null,
) : GallerySyncController {
    private val workManager = WorkManager.getInstance(context)

    override val pendingCount: Flow<Int> = localAssetDao.countByStatus(SyncStatus.PENDING)
    override val failedCount: Flow<Int> = combine(
        localAssetDao.countByStatus(SyncStatus.FAILED),
        assetMutationQueue?.failedCount ?: flowOf(0),
    ) { uploadFailures, metadataFailures -> uploadFailures + metadataFailures }
    override val uploadingCount: Flow<Int> = localAssetDao.countByStatus(SyncStatus.UPLOADING)
    override val uploadedCount: Flow<Int> = localAssetDao.countByStatus(SyncStatus.UPLOADED)
    val maxParallelUploads: StateFlow<Int> = settingsStore.maxParallelUploads
    val uploadsPaused: StateFlow<Boolean> = settingsStore.uploadsPaused
    val backgroundSyncEnabled: StateFlow<Boolean> = settingsStore.backgroundSyncEnabled
    val wifiOnly: StateFlow<Boolean> = settingsStore.wifiOnly
    val includeVideos: StateFlow<Boolean> = settingsStore.includeVideos
    val offlineImageCacheEnabled: StateFlow<Boolean> = settingsStore.offlineImageCacheEnabled
    val imageCacheLimitMb: StateFlow<Int> = settingsStore.imageCacheLimitMb
    val offlineImageCacheStatus = offlineImageCache?.status

    override suspend fun scanAndSync() {
        scanner.scan()
        enqueueSync()
    }

    override suspend fun retryFailed() {
        localAssetDao.retryFailed()
        assetMutationQueue?.retryFailed()
        enqueueSync()
    }

    fun enqueueSync() {
        enqueueSync(ExistingWorkPolicy.KEEP)
    }

    private fun enqueueSync(policy: ExistingWorkPolicy) {
        if (settingsStore.areUploadsPaused()) return

        val request = OneTimeWorkRequestBuilder<MediaSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                UPLOAD_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()
        workManager.enqueueUniqueWork(
            MediaSyncWorker.WORK_NAME,
            policy,
            request,
        )
    }

    fun restoreBackgroundSync(isLoggedIn: Boolean) {
        if (isLoggedIn && settingsStore.isBackgroundSyncEnabled()) {
            scheduleBackgroundSync()
        } else {
            workManager.cancelUniqueWork(PeriodicMediaScanWorker.WORK_NAME)
        }
    }

    fun scheduleBackgroundSync() {
        if (!settingsStore.isBackgroundSyncEnabled()) return

        val request = PeriodicWorkRequestBuilder<PeriodicMediaScanWorker>(
            BACKGROUND_SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PeriodicMediaScanWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun setMaxParallelUploads(value: Int) {
        settingsStore.setMaxParallelUploads(value)
    }

    fun setUploadsPaused(paused: Boolean) {
        settingsStore.setUploadsPaused(paused)
        if (paused) {
            workManager.cancelUniqueWork(MediaSyncWorker.WORK_NAME)
        } else {
            enqueueSync(ExistingWorkPolicy.REPLACE)
        }
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        settingsStore.setBackgroundSyncEnabled(enabled)
        if (enabled) {
            scheduleBackgroundSync()
        } else {
            workManager.cancelUniqueWork(PeriodicMediaScanWorker.WORK_NAME)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        settingsStore.setWifiOnly(enabled)
        if (settingsStore.isBackgroundSyncEnabled()) scheduleBackgroundSync()
        enqueueSync(ExistingWorkPolicy.REPLACE)
        offlineImageCache?.reschedule()
    }

    suspend fun setIncludeVideos(enabled: Boolean) {
        settingsStore.setIncludeVideos(enabled)
        if (enabled) localAssetDao.retrySkippedVideos()
        enqueueSync(ExistingWorkPolicy.REPLACE)
    }

    fun cancelAllSync() {
        workManager.cancelUniqueWork(MediaSyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(PeriodicMediaScanWorker.WORK_NAME)
        assetMutationQueue?.cancelWork()
        offlineImageCache?.cancel()
    }

    fun setOfflineImageCacheEnabled(enabled: Boolean) {
        offlineImageCache?.setEnabled(enabled)
    }

    suspend fun setImageCacheLimitMb(value: Int) {
        offlineImageCache?.setLimitMb(value)
    }

    fun downloadOfflineImages() {
        offlineImageCache?.enqueue()
    }

    suspend fun clearOfflineImageCache() {
        offlineImageCache?.clearAndDisable()
    }

    suspend fun clearOfflineImageCacheForLogout() {
        offlineImageCache?.clearForAccountChange()
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (settingsStore.isWifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED,
        )
        .build()

    private companion object {
        const val BACKGROUND_SYNC_INTERVAL_HOURS = 1L
        const val UPLOAD_BACKOFF_SECONDS = 30L
    }
}
