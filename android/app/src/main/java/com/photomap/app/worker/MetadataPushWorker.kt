package com.photomap.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photomap.app.PhotoMapApplication
import com.photomap.app.data.local.RemoteAssetOpType
import com.photomap.app.data.local.RemoteAssetPendingOpEntity
import com.photomap.app.data.network.ArchiveRequest
import com.photomap.app.data.network.FavoriteRequest
import com.photomap.app.data.repository.parseStatePayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.min

class MetadataPushWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container
    private val pendingOpDao = container.database.remoteAssetPendingOpDao()
    private val remoteAssetDao = container.database.remoteAssetDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (container.tokenStore.accessToken() == null) return@withContext Result.failure()

        pendingOpDao.resetInterrupted(System.currentTimeMillis())
        var retryRequired = false
        var pushedAny = false
        while (true) {
            val operations = pendingOpDao.getPendingReadyOps(
                nowMillis = System.currentTimeMillis(),
                limit = OPERATIONS_PER_BATCH,
            )
            if (operations.isEmpty()) break

            for (operation in operations) {
                when (push(operation)) {
                    PushResult.SUCCESS -> pushedAny = true
                    PushResult.RETRY -> retryRequired = true
                    PushResult.AUTHENTICATION_REQUIRED -> return@withContext Result.failure()
                    PushResult.PERMANENT_FAILURE -> Unit
                }
            }
        }

        if (pushedAny) container.galleryRepository.syncAssetMetadata(force = true)
        pendingOpDao.deleteCompletedOlderThan(System.currentTimeMillis() - COMPLETED_RETENTION_MILLIS)
        if (retryRequired) Result.retry() else Result.success()
    }

    private suspend fun push(operation: RemoteAssetPendingOpEntity): PushResult {
        val now = System.currentTimeMillis()
        pendingOpDao.markInProgress(operation.opId, now)
        return try {
            callBackend(operation)
            pendingOpDao.markCompleted(operation.opId, System.currentTimeMillis())
            PushResult.SUCCESS
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            when (error.code()) {
                HTTP_UNAUTHORIZED -> {
                    container.tokenStore.clear()
                    markFailure(operation, error, retryable = false)
                    PushResult.AUTHENTICATION_REQUIRED
                }
                HTTP_NOT_FOUND -> handleNotFound(operation, error)
                HTTP_TOO_MANY_REQUESTS -> {
                    markFailure(operation, error, retryable = true)
                    PushResult.RETRY
                }
                else -> {
                    val retryable = error.code() >= HTTP_SERVER_ERROR
                    markFailure(operation, error, retryable)
                    if (retryable) PushResult.RETRY else PushResult.PERMANENT_FAILURE
                }
            }
        } catch (error: IOException) {
            markFailure(operation, error, retryable = true)
            PushResult.RETRY
        } catch (error: Exception) {
            markFailure(operation, error, retryable = false)
            PushResult.PERMANENT_FAILURE
        }
    }

    private suspend fun callBackend(operation: RemoteAssetPendingOpEntity) {
        when (operation.opType) {
            RemoteAssetOpType.FAVORITE -> container.api.updateFavorite(
                operation.assetId,
                FavoriteRequest(parseStatePayload(operation.payloadJson)),
            )
            RemoteAssetOpType.ARCHIVE -> container.api.updateArchive(
                operation.assetId,
                ArchiveRequest(parseStatePayload(operation.payloadJson)),
            )
            RemoteAssetOpType.TRASH -> container.api.trashAsset(operation.assetId)
            RemoteAssetOpType.RESTORE -> container.api.restoreAsset(operation.assetId)
            RemoteAssetOpType.HARD_DELETE -> container.api.deleteAsset(operation.assetId)
            else -> error("Unknown metadata operation: ${operation.opType}")
        }
    }

    private suspend fun handleNotFound(
        operation: RemoteAssetPendingOpEntity,
        error: HttpException,
    ): PushResult {
        container.galleryRepository.syncAssetMetadata(force = true)
        return if (remoteAssetDao.getAsset(operation.assetId) == null) {
            pendingOpDao.markCompleted(operation.opId, System.currentTimeMillis())
            PushResult.SUCCESS
        } else {
            markFailure(operation, error, retryable = true)
            PushResult.RETRY
        }
    }

    private suspend fun markFailure(
        operation: RemoteAssetPendingOpEntity,
        error: Exception,
        retryable: Boolean,
    ) {
        val attempt = operation.attemptCount + 1
        val now = System.currentTimeMillis()
        pendingOpDao.markFailed(
            opId = operation.opId,
            error = error.message ?: error::class.java.simpleName,
            nextRetryAt = if (retryable) now + retryDelayMillis(attempt) else null,
            attemptCount = attempt,
            updatedAt = now,
        )
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, MAX_RETRY_EXPONENT)
        return min(BASE_RETRY_DELAY_MILLIS * (1L shl exponent), MAX_RETRY_DELAY_MILLIS)
    }

    companion object {
        const val WORK_NAME = "asset-metadata-push"
        private const val OPERATIONS_PER_BATCH = 50
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
        private const val BASE_RETRY_DELAY_MILLIS = 30_000L
        private const val MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1_000L
        private const val MAX_RETRY_EXPONENT = 10
        private const val COMPLETED_RETENTION_MILLIS = 24 * 60 * 60 * 1_000L
    }

    private enum class PushResult {
        SUCCESS,
        RETRY,
        AUTHENTICATION_REQUIRED,
        PERMANENT_FAILURE,
    }
}
