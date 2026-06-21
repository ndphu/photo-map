package com.photomap.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import coil3.network.HttpException as CoilHttpException
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import com.photomap.app.PhotoMapApplication
import com.photomap.app.data.cache.CloudImageVariant
import com.photomap.app.data.cache.cloudImageRequest
import com.photomap.app.data.gallery.SignedUrlVariant
import com.photomap.app.data.gallery.isExpiredSignedUrlError
import com.photomap.app.data.local.RemoteAssetEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class OfflineImageCacheWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container
    private val coordinator = container.offlineImageCacheCoordinator
    private val remoteAssetDao = container.database.remoteAssetDao()
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (container.tokenStore.accessToken() == null) return@withContext Result.failure()
        if (!container.syncSettingsStore.isOfflineImageCacheEnabled()) return@withContext Result.success()

        val assets = remoteAssetDao.listOfflineCacheCandidates()
        val tasks = buildOfflineCacheTasks(assets)
        createNotificationChannel()
        setForeground(createForegroundInfo(0, tasks.size))
        coordinator.updateProgress(0, tasks.size)

        var completed = 0
        try {
            for (task in tasks) {
                when (cache(task)) {
                    CacheResult.SUCCESS, CacheResult.SKIPPED -> Unit
                    CacheResult.AUTHENTICATION_REQUIRED -> {
                        container.tokenStore.clear()
                        coordinator.fail("Session expired")
                        return@withContext Result.failure()
                    }
                    CacheResult.RETRY -> {
                        coordinator.fail("Network unavailable")
                        return@withContext Result.retry()
                    }
                }
                completed += 1
                coordinator.updateProgress(completed, tasks.size)
                setProgress(androidx.work.workDataOf(PROGRESS_COMPLETED to completed, PROGRESS_TOTAL to tasks.size))
                setForeground(createForegroundInfo(completed, tasks.size))
            }
            coordinator.complete()
            Result.success()
        } catch (error: CancellationException) {
            coordinator.stopped()
            throw error
        } catch (_: IOException) {
            coordinator.fail("Network unavailable")
            Result.retry()
        } catch (_: Exception) {
            coordinator.fail("Cannot cache images")
            Result.retry()
        }
    }

    private suspend fun cache(task: OfflineCacheTask): CacheResult {
        var url = task.url
        if (url == null) {
            val refresh = refreshUrl(task, failedUrl = null)
            if (refresh != CacheResult.SUCCESS) return refresh
            url = currentUrl(task)
        }
        if (url == null) return CacheResult.SKIPPED

        val firstError = execute(task, url)
        if (firstError == null) return CacheResult.SUCCESS
        val firstStatus = httpStatusCode(firstError)
        if (!isExpiredSignedUrlError(firstError)) return classifyFailure(firstError, firstStatus)

        val refresh = refreshUrl(task, url)
        if (refresh != CacheResult.SUCCESS) return refresh
        val refreshedUrl = currentUrl(task) ?: return CacheResult.SKIPPED
        val retryError = execute(task, refreshedUrl) ?: return CacheResult.SUCCESS
        return classifyFailure(retryError, httpStatusCode(retryError))
    }

    private suspend fun execute(task: OfflineCacheTask, url: String): Throwable? {
        val request = cloudImageRequest(
            context = applicationContext,
            assetId = task.assetId,
            variant = task.cloudVariant,
            url = url,
            prefetch = true,
        )
        return when (val result = container.imageCacheManager.imageLoader.execute(request)) {
            is SuccessResult -> null
            is ErrorResult -> result.throwable
            else -> IllegalStateException("Unknown image result")
        }
    }

    private suspend fun refreshUrl(task: OfflineCacheTask, failedUrl: String?): CacheResult {
        val result = container.galleryRepository.refreshSignedUrl(
            assetId = task.assetId,
            variant = task.signedUrlVariant,
            failedUrl = failedUrl,
        )
        val error = result.exceptionOrNull() ?: return CacheResult.SUCCESS
        return classifyFailure(error, httpStatusCode(error))
    }

    private suspend fun currentUrl(task: OfflineCacheTask): String? {
        val asset = remoteAssetDao.getAsset(task.assetId) ?: return null
        return when (task.cloudVariant) {
            CloudImageVariant.THUMBNAIL -> asset.thumbnailUrl
            CloudImageVariant.PREVIEW -> asset.previewUrl
            CloudImageVariant.POSTER_FRAME -> asset.posterFrameUrl
        }
    }

    private fun classifyFailure(error: Throwable, status: Int?): CacheResult = when {
        status == HTTP_UNAUTHORIZED -> CacheResult.AUTHENTICATION_REQUIRED
        status == HTTP_NOT_FOUND -> CacheResult.SKIPPED
        status == HTTP_TOO_MANY_REQUESTS -> CacheResult.RETRY
        status != null && status >= HTTP_SERVER_ERROR -> CacheResult.RETRY
        error is IOException -> CacheResult.RETRY
        else -> CacheResult.SKIPPED
    }

    private fun httpStatusCode(error: Throwable): Int? {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is HttpException -> return current.code()
                is CoilHttpException -> return current.response.code
            }
            current = current.cause
        }
        return null
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Offline image cache",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Thumbnail and preview download progress" },
        )
    }

    private fun createForegroundInfo(completed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading gallery for offline use")
            .setContentText("Cached $completed of $total")
            .setContentIntent(createContentIntent())
            .setProgress(total.coerceAtLeast(1), completed.coerceAtMost(total), total == 0)
            .setOngoing(completed < total)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createContentIntent(): PendingIntent? {
        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val PROGRESS_COMPLETED = "completed"
        const val PROGRESS_TOTAL = "total"
        private const val NOTIFICATION_CHANNEL_ID = "offline_image_cache"
        private const val NOTIFICATION_ID = 2002
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}

data class OfflineCacheTask(
    val assetId: String,
    val cloudVariant: CloudImageVariant,
    val signedUrlVariant: SignedUrlVariant,
    val url: String?,
)

fun buildOfflineCacheTasks(assetsOldestFirst: List<RemoteAssetEntity>): List<OfflineCacheTask> {
    val previews = assetsOldestFirst.mapNotNull { asset ->
        asset.previewKey?.let {
            OfflineCacheTask(asset.id, CloudImageVariant.PREVIEW, SignedUrlVariant.PREVIEW, asset.previewUrl)
        }
    }
    val thumbnails = assetsOldestFirst.mapNotNull { asset ->
        asset.thumbnailKey?.let {
            OfflineCacheTask(
                asset.id,
                CloudImageVariant.THUMBNAIL,
                SignedUrlVariant.THUMBNAIL,
                asset.thumbnailUrl,
            )
        }
    }
    return previews + thumbnails
}

private enum class CacheResult {
    SUCCESS,
    SKIPPED,
    RETRY,
    AUTHENTICATION_REQUIRED,
}
