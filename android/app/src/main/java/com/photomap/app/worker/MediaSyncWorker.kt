package com.photomap.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.photomap.app.PhotoMapApplication
import com.photomap.app.data.local.LocalAssetEntity
import com.photomap.app.data.local.SyncStatus
import com.photomap.app.data.network.CompleteUploadRequest
import com.photomap.app.data.network.CreateUploadSessionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class MediaSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container
    private val dao = container.database.localAssetDao()
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deviceId = container.tokenStore.deviceId() ?: return@withContext Result.failure()
        val userId = container.tokenStore.userId() ?: return@withContext Result.failure()
        if (container.tokenStore.accessToken() == null) return@withContext Result.failure()

        dao.resetInterruptedUploads()
        val total = dao.countByStatusOnce(SyncStatus.PENDING)
        if (total == 0) return@withContext Result.success()

        createNotificationChannel()
        setForeground(createForegroundInfo(0, total))

        val completed = AtomicInteger(0)
        val maxParallelUploads = container.syncSettingsStore.currentMaxParallelUploads()
        while (true) {
            val pending = dao.pending(listOf(SyncStatus.PENDING), ASSETS_PER_BATCH)
            if (pending.isEmpty()) break
            uploadBatch(pending, deviceId, userId, maxParallelUploads, completed, total)
        }

        Result.success()
    }

    private suspend fun uploadBatch(
        assets: List<LocalAssetEntity>,
        deviceId: String,
        userId: String,
        maxParallelUploads: Int,
        completed: AtomicInteger,
        total: Int,
    ) = supervisorScope {
        val semaphore = Semaphore(maxParallelUploads)
        assets.map { asset ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        uploadAsset(asset, deviceId, userId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        dao.updateStatus(
                            asset.localAssetId,
                            SyncStatus.FAILED,
                            error.message ?: error::class.java.simpleName,
                        )
                    } finally {
                        val current = completed.incrementAndGet()
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            createNotification(current, total),
                        )
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun uploadAsset(asset: LocalAssetEntity, deviceId: String, userId: String) {
        ensureSessionIsActive(userId)
        dao.updateStatus(asset.localAssetId, SyncStatus.UPLOADING, null)

        val uri = Uri.parse(asset.uri)
        val checksum = sha256(uri)
        val session = container.api.createUploadSession(
            CreateUploadSessionRequest(
                deviceId = deviceId,
                localAssetId = asset.localAssetId,
                mediaType = asset.mediaType,
                mimeType = asset.mimeType,
                originalFilename = asset.displayName,
                fileSizeBytes = asset.sizeBytes,
                expectedChecksumSha256 = checksum,
            ),
        )
        val variants = container.variantGenerator.generate(uri, asset.mediaType)

        putOriginal(session.uploadUrls.original, uri, asset.mimeType, asset.sizeBytes)
        putBytes(session.uploadUrls.thumbnail, variants.thumbnail, "image/webp")
        putBytes(session.uploadUrls.preview, variants.preview, "image/webp")
        session.uploadUrls.posterFrame?.let { url ->
            variants.posterFrame?.let { putBytes(url, it, "image/webp") }
        }

        ensureSessionIsActive(userId)
        val metadata = container.metadataExtractor.extract(asset)
        val response = container.api.completeUpload(
            session.id,
            CompleteUploadRequest(
                checksumSha256 = checksum,
                takenAt = metadata.takenAt,
                takenAtSource = metadata.takenAtSource,
                timezoneOffsetMinutes = metadata.timezoneOffsetMinutes,
                width = asset.width,
                height = asset.height,
                orientation = metadata.orientation,
                durationMs = asset.durationMs,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                cameraMake = metadata.cameraMake,
                cameraModel = metadata.cameraModel,
                software = metadata.software,
                localCreatedAt = asset.takenAt?.let(Instant::ofEpochMilli)?.toString(),
                localModifiedAt = asset.modifiedAt?.let(Instant::ofEpochMilli)?.toString(),
            ),
        )

        dao.markUploaded(
            id = asset.localAssetId,
            status = SyncStatus.UPLOADED,
            remoteAssetId = response.assetId,
            syncedAt = System.currentTimeMillis(),
        )
    }

    private fun ensureSessionIsActive(userId: String) {
        if (container.tokenStore.accessToken() == null || container.tokenStore.userId() != userId) {
            throw CancellationException("Authentication session ended")
        }
    }

    private fun putOriginal(url: String, uri: Uri, mimeType: String, size: Long) {
        val body = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()

            override fun contentLength(): Long = size

            override fun writeTo(sink: BufferedSink) {
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: error("Unable to open media")
            }
        }
        executePut(url, body)
    }

    private fun putBytes(url: String, bytes: ByteArray, mimeType: String) {
        executePut(url, bytes.toRequestBody(mimeType.toMediaType()))
    }

    private fun executePut(url: String, body: RequestBody) {
        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()
        container.uploadClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "R2 upload failed with HTTP ${response.code}" }
        }
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Unable to open media")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Media uploads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Photo and video backup progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(completed: Int, total: Int): ForegroundInfo {
        val notification = createNotification(completed, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(completed: Int, total: Int) = NotificationCompat.Builder(
        applicationContext,
        NOTIFICATION_CHANNEL_ID,
    )
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Backing up media")
        .setContentText("Processed $completed of $total")
        .setContentIntent(createContentIntent())
        .setProgress(total, completed.coerceAtMost(total), false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

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
        const val WORK_NAME = "media-sync"
        private const val ASSETS_PER_BATCH = 64
        private const val NOTIFICATION_CHANNEL_ID = "media_uploads"
        private const val NOTIFICATION_ID = 1001
    }
}
