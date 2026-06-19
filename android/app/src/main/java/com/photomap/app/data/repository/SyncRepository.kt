package com.photomap.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.local.SyncStatus
import com.photomap.app.data.media.MediaStoreScanner
import com.photomap.app.worker.MediaSyncWorker
import kotlinx.coroutines.flow.Flow

class SyncRepository(
    private val context: Context,
    private val scanner: MediaStoreScanner,
    private val localAssetDao: LocalAssetDao,
) {
    val pendingCount: Flow<Int> = localAssetDao.countByStatus(SyncStatus.PENDING)
    val failedCount: Flow<Int> = localAssetDao.countByStatus(SyncStatus.FAILED)

    suspend fun scanAndSync() {
        scanner.scan()
        enqueueSync()
    }

    suspend fun retryFailed() {
        localAssetDao.retryFailed()
        enqueueSync()
    }

    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<MediaSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MediaSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
