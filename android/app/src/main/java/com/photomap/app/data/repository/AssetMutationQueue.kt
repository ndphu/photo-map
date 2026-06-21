package com.photomap.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.photomap.app.data.local.PhotoMapDatabase
import com.photomap.app.data.local.RemoteAssetOpStatus
import com.photomap.app.data.local.RemoteAssetOpType
import com.photomap.app.data.local.RemoteAssetPendingOpDao
import com.photomap.app.data.local.RemoteAssetPendingOpEntity
import com.photomap.app.data.local.RemoteAssetDao
import com.photomap.app.worker.MetadataPushWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

class AssetMutationQueue(
    context: Context,
    private val database: PhotoMapDatabase,
    private val pendingOpDao: RemoteAssetPendingOpDao,
    private val remoteAssetDao: RemoteAssetDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val workManager = WorkManager.getInstance(context)
    val failedCount: Flow<Int> = pendingOpDao.observeFailedCount()

    suspend fun setFavorite(assetId: String, value: Boolean) {
        enqueueStateSetting(assetId, RemoteAssetOpType.FAVORITE, value) {
            remoteAssetDao.markFavoriteLocal(assetId, value)
        }
    }

    suspend fun setArchived(assetId: String, value: Boolean) {
        enqueueStateSetting(assetId, RemoteAssetOpType.ARCHIVE, value) {
            remoteAssetDao.markArchivedLocal(assetId, value)
        }
    }

    suspend fun trash(assetId: String) {
        enqueueTransition(assetId, RemoteAssetOpType.TRASH, isTrashed = true)
    }

    suspend fun restore(assetId: String) {
        enqueueTransition(assetId, RemoteAssetOpType.RESTORE, isTrashed = false)
    }

    suspend fun hardDelete(assetId: String) {
        val now = nowMillis()
        database.withTransaction {
            pendingOpDao.deleteAllActiveForAsset(assetId)
            pendingOpDao.insert(newOperation(assetId, RemoteAssetOpType.HARD_DELETE, null, now))
            remoteAssetDao.markTrashedLocal(assetId, true)
        }
        enqueueWork()
    }

    suspend fun retryFailed() {
        pendingOpDao.retryFailed(nowMillis())
        enqueueWork()
    }

    suspend fun clearAll() {
        cancelWork()
        pendingOpDao.clearAll()
    }

    fun enqueueWork() {
        val request = OneTimeWorkRequestBuilder<MetadataPushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WORK_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()
        workManager.enqueueUniqueWork(
            MetadataPushWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelWork() {
        workManager.cancelUniqueWork(MetadataPushWorker.WORK_NAME)
    }

    private suspend fun enqueueStateSetting(
        assetId: String,
        opType: String,
        value: Boolean,
        updateLocal: suspend () -> Unit,
    ) {
        val now = nowMillis()
        var enqueued = false
        database.withTransaction {
            if (!pendingOpDao.hasActiveHardDelete(assetId)) {
                pendingOpDao.deleteActiveByAssetAndType(assetId, opType)
                pendingOpDao.insert(newOperation(assetId, opType, statePayload(value), now))
                updateLocal()
                enqueued = true
            }
        }
        if (enqueued) enqueueWork()
    }

    private suspend fun enqueueTransition(assetId: String, opType: String, isTrashed: Boolean) {
        val now = nowMillis()
        var enqueued = false
        database.withTransaction {
            if (!pendingOpDao.hasActiveHardDelete(assetId)) {
                pendingOpDao.deleteActiveTrashRestore(assetId)
                pendingOpDao.insert(newOperation(assetId, opType, null, now))
                remoteAssetDao.markTrashedLocal(assetId, isTrashed)
                enqueued = true
            }
        }
        if (enqueued) enqueueWork()
    }

    private fun newOperation(
        assetId: String,
        opType: String,
        payloadJson: String?,
        now: Long,
    ) = RemoteAssetPendingOpEntity(
        opId = UUID.randomUUID().toString(),
        assetId = assetId,
        opType = opType,
        payloadJson = payloadJson,
        status = RemoteAssetOpStatus.PENDING,
        attemptCount = 0,
        nextRetryAt = null,
        lastError = null,
        createdAt = now,
        updatedAt = now,
    )

    private companion object {
        const val WORK_BACKOFF_SECONDS = 30L
    }
}

internal fun statePayload(value: Boolean): String = "{\"value\":$value}"

internal fun parseStatePayload(payloadJson: String?): Boolean = when (payloadJson) {
    statePayload(true) -> true
    statePayload(false) -> false
    else -> error("Invalid state-setting operation payload")
}
