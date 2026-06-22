package com.photomap.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.local.MetadataBackfillStatus
import com.photomap.app.data.media.hasMediaLocationAccess
import com.photomap.app.worker.AssetMetadataBackfillWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

data class AssetMetadataBackfillState(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val needsPermission: Boolean = false,
    val errorMessage: String? = null,
)

class AssetMetadataBackfillCoordinator(
    context: Context,
    private val localAssetDao: LocalAssetDao,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val _state = MutableStateFlow(
        AssetMetadataBackfillState(needsPermission = !hasMediaLocationAccess(appContext)),
    )
    val state: StateFlow<AssetMetadataBackfillState> = _state.asStateFlow()
    val pendingCount: Flow<Int> = localAssetDao.countMetadataBackfillStatus(MetadataBackfillStatus.PENDING)
    val failedCount: Flow<Int> = localAssetDao.countMetadataBackfillStatus(MetadataBackfillStatus.FAILED)

    fun restore(isLoggedIn: Boolean) {
        if (isLoggedIn) enqueue()
    }

    fun enqueue() {
        val needsPermission = !hasMediaLocationAccess(appContext)
        _state.value = _state.value.copy(needsPermission = needsPermission)
        if (needsPermission) return
        val request = OneTimeWorkRequestBuilder<AssetMetadataBackfillWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    suspend fun retry() {
        localAssetDao.retryMetadataBackfill()
        enqueue()
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
        _state.value = _state.value.copy(running = false)
    }

    fun updateProgress(completed: Int, total: Int) {
        _state.value = AssetMetadataBackfillState(running = true, completed = completed, total = total)
    }

    fun complete() {
        _state.value = AssetMetadataBackfillState()
    }

    fun permissionRequired() {
        _state.value = _state.value.copy(running = false, needsPermission = true)
    }

    fun fail(message: String) {
        _state.value = _state.value.copy(running = false, errorMessage = message)
    }

    companion object {
        const val WORK_NAME = "asset-metadata-backfill"
        private const val BACKOFF_SECONDS = 30L
    }
}
