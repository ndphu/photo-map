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
import androidx.work.workDataOf
import com.photomap.app.PhotoMapApplication
import com.photomap.app.data.local.MetadataBackfillStatus
import com.photomap.app.data.media.ExtractedMetadata
import com.photomap.app.data.media.hasMediaLocationAccess
import com.photomap.app.data.network.ReplaceAssetMetadataRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AssetMetadataBackfillWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container
    private val dao = container.database.localAssetDao()
    private val coordinator = container.assetMetadataBackfillCoordinator
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (container.tokenStore.accessToken() == null) return@withContext Result.failure()
        if (!hasMediaLocationAccess(applicationContext)) {
            coordinator.permissionRequired()
            return@withContext Result.success()
        }

        val total = dao.countPendingMetadataBackfillOnce()
        createNotificationChannel()
        setForeground(createForegroundInfo(0, total))
        coordinator.updateProgress(0, total)
        var completed = 0
        var updatedAny = false

        try {
            while (true) {
                val assets = dao.metadataBackfillCandidates(BATCH_SIZE)
                if (assets.isEmpty()) break
                for (asset in assets) {
                    val remoteAssetId = asset.remoteAssetId ?: continue
                    val metadata = try {
                        container.metadataExtractor.extractForBackfill(asset)
                    } catch (error: SecurityException) {
                        coordinator.permissionRequired()
                        return@withContext Result.success()
                    } catch (error: IOException) {
                        dao.markMetadataBackfillIssue(
                            asset.localAssetId,
                            MetadataBackfillStatus.SKIPPED,
                            "Local media is unavailable",
                        )
                        completed += 1
                        updateProgress(completed, total)
                        continue
                    } catch (error: RuntimeException) {
                        dao.markMetadataBackfillIssue(
                            asset.localAssetId,
                            MetadataBackfillStatus.SKIPPED,
                            "Cannot read local metadata",
                        )
                        completed += 1
                        updateProgress(completed, total)
                        continue
                    }

                    when (replaceMetadata(remoteAssetId, metadata)) {
                        PushResult.SUCCESS -> {
                            dao.markMetadataBackfillCompleted(asset.localAssetId, System.currentTimeMillis())
                            updatedAny = true
                        }
                        PushResult.SKIPPED -> dao.markMetadataBackfillIssue(
                            asset.localAssetId,
                            MetadataBackfillStatus.SKIPPED,
                            "Cloud asset is unavailable",
                        )
                        PushResult.FAILED -> dao.markMetadataBackfillIssue(
                            asset.localAssetId,
                            MetadataBackfillStatus.FAILED,
                            "Cannot update cloud metadata",
                        )
                        PushResult.RETRY -> {
                            coordinator.fail("Network unavailable")
                            return@withContext Result.retry()
                        }
                        PushResult.AUTHENTICATION_REQUIRED -> {
                            container.tokenStore.clear()
                            coordinator.fail("Session expired")
                            return@withContext Result.failure()
                        }
                    }
                    completed += 1
                    updateProgress(completed, total)
                }
            }

            if (updatedAny) container.galleryRepository.syncAssetMetadata(force = true)
            coordinator.complete()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            coordinator.fail("Network unavailable")
            Result.retry()
        }
    }

    private suspend fun replaceMetadata(assetId: String, metadata: ExtractedMetadata): PushResult = try {
        container.api.replaceAssetMetadata(
            assetId,
            ReplaceAssetMetadataRequest(
                takenAt = metadata.takenAt,
                takenAtSource = metadata.takenAtSource,
                timezoneOffsetMinutes = metadata.timezoneOffsetMinutes,
                orientation = metadata.orientation,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                cameraMake = metadata.cameraMake,
                cameraModel = metadata.cameraModel,
                software = metadata.software,
            ),
        )
        PushResult.SUCCESS
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpException) {
        when {
            error.code() == 401 -> PushResult.AUTHENTICATION_REQUIRED
            error.code() == 404 -> PushResult.SKIPPED
            error.code() == 429 || error.code() >= 500 -> PushResult.RETRY
            else -> PushResult.FAILED
        }
    } catch (_: IOException) {
        PushResult.RETRY
    }

    private suspend fun updateProgress(completed: Int, total: Int) {
        coordinator.updateProgress(completed, total)
        setProgress(workDataOf(PROGRESS_COMPLETED to completed, PROGRESS_TOTAL to total))
        setForeground(createForegroundInfo(completed, total))
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Photo metadata", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun createForegroundInfo(completed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Updating photo metadata")
            .setContentText("Updated $completed of $total")
            .setContentIntent(contentIntent())
            .setProgress(total.coerceAtLeast(1), completed.coerceAtMost(total), total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(completed < total)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun contentIntent(): PendingIntent? {
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

    private enum class PushResult { SUCCESS, SKIPPED, FAILED, RETRY, AUTHENTICATION_REQUIRED }

    companion object {
        const val PROGRESS_COMPLETED = "completed"
        const val PROGRESS_TOTAL = "total"
        private const val BATCH_SIZE = 100
        private const val CHANNEL_ID = "asset_metadata_backfill"
        private const val NOTIFICATION_ID = 2003
    }
}
