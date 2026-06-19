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
import com.photomap.app.data.network.UpdateUploadSessionStatusRequest
import com.photomap.app.data.network.UploadSessionDto
import com.photomap.app.data.network.UploadSessionResponse
import com.photomap.app.data.network.UploadUrlsDto
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
import retrofit2.HttpException
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

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
        val uploadStatuses = listOf(SyncStatus.PENDING, SyncStatus.FAILED)
        val total = dao.countReadyForUpload(uploadStatuses, System.currentTimeMillis())
        if (total == 0) {
            return@withContext if (dao.countByStatusOnce(SyncStatus.FAILED) > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        }

        createNotificationChannel()
        setForeground(createForegroundInfo(0, total))

        val completed = AtomicInteger(0)
        val maxParallelUploads = container.syncSettingsStore.currentMaxParallelUploads()
        val includeVideos = container.syncSettingsStore.shouldIncludeVideos()
        var retryRequired = false
        var authenticationRequired = false
        while (true) {
            val pending = dao.readyForUpload(
                uploadStatuses,
                System.currentTimeMillis(),
                ASSETS_PER_BATCH,
            )
            if (pending.isEmpty()) break
            val results = uploadBatch(
                pending,
                deviceId,
                userId,
                maxParallelUploads,
                includeVideos,
                completed,
                total,
            )
            retryRequired = retryRequired || results.any { it == AssetUploadResult.RETRY }
            authenticationRequired = authenticationRequired || results.any {
                it == AssetUploadResult.AUTHENTICATION_REQUIRED
            }
            if (authenticationRequired) break
        }

        when {
            authenticationRequired -> Result.failure()
            retryRequired || dao.countByStatusOnce(SyncStatus.FAILED) > 0 -> Result.retry()
            else -> Result.success()
        }
    }

    private suspend fun uploadBatch(
        assets: List<LocalAssetEntity>,
        deviceId: String,
        userId: String,
        maxParallelUploads: Int,
        includeVideos: Boolean,
        completed: AtomicInteger,
        total: Int,
    ): List<AssetUploadResult> = supervisorScope {
        val semaphore = Semaphore(maxParallelUploads)
        assets.map { asset ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        if (asset.mediaType == MEDIA_TYPE_VIDEO && !includeVideos) {
                            dao.updateStatus(asset.localAssetId, SyncStatus.SKIPPED, null)
                            AssetUploadResult.SUCCESS
                        } else {
                            uploadAsset(asset, deviceId, userId)
                            AssetUploadResult.SUCCESS
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        handleUploadFailure(asset, error)
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
        dao.beginUploadAttempt(asset.localAssetId)

        val uri = Uri.parse(asset.uri)
        val checksum = sha256(uri)
        var uploadSessionId = asset.uploadSessionId
        try {
            val existingSessionId = uploadSessionId
            var resolution = if (existingSessionId != null) {
                resolveSession(resumeUploadSession(existingSessionId))
            } else {
                resolveSession(
                    container.api.createUploadSession(
                        CreateUploadSessionRequest(
                            deviceId = deviceId,
                            localAssetId = asset.localAssetId,
                            mediaType = asset.mediaType,
                            mimeType = asset.mimeType,
                            originalFilename = asset.displayName,
                            fileSizeBytes = asset.sizeBytes,
                            expectedChecksumSha256 = checksum,
                        ),
                    ),
                )
            }

            if (resolution is SessionResolution.Uploaded) {
                markUploaded(asset.localAssetId, resolution.assetId)
                return
            }

            var active = resolution as SessionResolution.Active
            val sessionId = active.session.id
            uploadSessionId = sessionId
            dao.saveUploadSession(asset.localAssetId, sessionId)

            if (active.session.status !in COMPLETE_READY_SESSION_STATUSES) {
                updateBackendStatus(sessionId, UPLOAD_STATUS_UPLOADING)
                try {
                    uploadMedia(active.uploadUrls, uri, asset)
                } catch (error: R2UploadException) {
                    if (error.statusCode != HTTP_FORBIDDEN) throw error

                    resolution = resolveSession(resumeUploadSession(sessionId))
                    if (resolution is SessionResolution.Uploaded) {
                        markUploaded(asset.localAssetId, resolution.assetId)
                        return
                    }
                    active = resolution as SessionResolution.Active
                    uploadMedia(active.uploadUrls, uri, asset)
                }
                updateBackendStatus(sessionId, UPLOAD_STATUS_UPLOADED)
            }

            ensureSessionIsActive(userId)
            val response = try {
                completeUpload(sessionId, checksum, asset)
            } catch (error: HttpException) {
                if (error.code() != HTTP_CONFLICT) throw error

                resolution = resolveSession(resumeUploadSession(sessionId))
                if (resolution is SessionResolution.Uploaded) {
                    markUploaded(asset.localAssetId, resolution.assetId)
                    return
                }
                throw error
            }
            markUploaded(asset.localAssetId, response.assetId)
        } catch (error: Exception) {
            val failedSessionId = uploadSessionId
            if (
                error !is CancellationException &&
                failedSessionId != null &&
                !isAuthenticationError(error) &&
                !isMissingSessionError(error)
            ) {
                runCatching {
                    updateBackendStatus(
                        failedSessionId,
                        UPLOAD_STATUS_FAILED,
                        error.message ?: error::class.java.simpleName,
                    )
                }
            }
            throw error
        }
    }

    private fun resolveSession(response: UploadSessionResponse): SessionResolution = when (response.status) {
        CREATE_STATUS_ALREADY_UPLOADED,
        CREATE_STATUS_DUPLICATE_FOUND,
        RESUME_STATUS_COMPLETED -> SessionResolution.Uploaded(
            requireNotNull(response.asset?.id) { "Upload response is missing asset" },
        )

        CREATE_STATUS_EXISTING_SESSION,
        CREATE_STATUS_CREATED,
        RESUME_STATUS_RESUMED -> SessionResolution.Active(
            session = requireNotNull(response.session) { "Upload response is missing session" },
            uploadUrls = requireNotNull(response.uploadUrls) {
                "Upload response is missing presigned URLs"
            },
        )

        else -> error("Unsupported upload session status: ${response.status}")
    }

    private suspend fun resumeUploadSession(uploadSessionId: String): UploadSessionResponse = try {
        container.api.resumeUploadSession(uploadSessionId)
    } catch (error: HttpException) {
        if (error.code() in SESSION_RESET_HTTP_CODES) {
            throw SessionResetRequiredException(error)
        }
        throw error
    }

    private suspend fun updateBackendStatus(
        uploadSessionId: String,
        status: String,
        errorMessage: String? = null,
    ) {
        container.api.updateUploadSessionStatus(
            uploadSessionId,
            UpdateUploadSessionStatusRequest(status, errorMessage),
        )
    }

    private fun uploadMedia(uploadUrls: UploadUrlsDto, uri: Uri, asset: LocalAssetEntity) {
        putOriginal(uploadUrls.original, uri, asset.mimeType, asset.sizeBytes)

        val variants = runCatching {
            container.variantGenerator.generate(uri, asset.mediaType)
        }.getOrNull() ?: return
        putBytes(uploadUrls.thumbnail, variants.thumbnail, "image/webp")
        putBytes(uploadUrls.preview, variants.preview, "image/webp")
        uploadUrls.posterFrame?.let { url ->
            variants.posterFrame?.let { putBytes(url, it, "image/webp") }
        }
    }

    private suspend fun completeUpload(
        uploadSessionId: String,
        checksum: String,
        asset: LocalAssetEntity,
    ) = container.api.completeUpload(
        uploadSessionId,
        container.metadataExtractor.extract(asset).let { metadata ->
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
            )
        },
    )

    private suspend fun handleUploadFailure(
        asset: LocalAssetEntity,
        error: Exception,
    ): AssetUploadResult {
        val message = error.message ?: error::class.java.simpleName
        if (isAuthenticationError(error)) {
            container.tokenStore.clear()
            dao.markFailed(asset.localAssetId, message, null)
            return AssetUploadResult.AUTHENTICATION_REQUIRED
        }

        if (isMissingSessionError(error)) {
            dao.clearUploadSession(asset.localAssetId)
        }
        val attempt = asset.uploadAttemptCount + 1
        dao.markFailed(
            id = asset.localAssetId,
            error = message,
            nextRetryAt = System.currentTimeMillis() + retryDelayMillis(attempt),
        )
        return AssetUploadResult.RETRY
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, MAX_RETRY_EXPONENT)
        return min(BASE_RETRY_DELAY_MILLIS * (1L shl exponent), MAX_RETRY_DELAY_MILLIS)
    }

    private fun isAuthenticationError(error: Exception): Boolean =
        error is HttpException && error.code() == HTTP_UNAUTHORIZED

    private fun isMissingSessionError(error: Exception): Boolean =
        error is SessionResetRequiredException ||
            error is HttpException && (error.code() == HTTP_FORBIDDEN || error.code() == HTTP_NOT_FOUND)

    private suspend fun markUploaded(localAssetId: String, remoteAssetId: String) {
        dao.markUploaded(
            id = localAssetId,
            status = SyncStatus.UPLOADED,
            remoteAssetId = remoteAssetId,
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
            if (!response.isSuccessful) {
                throw R2UploadException(response.code)
            }
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
        private const val CREATE_STATUS_ALREADY_UPLOADED = "already_uploaded"
        private const val CREATE_STATUS_DUPLICATE_FOUND = "duplicate_found"
        private const val CREATE_STATUS_EXISTING_SESSION = "existing_session"
        private const val CREATE_STATUS_CREATED = "created"
        private const val RESUME_STATUS_RESUMED = "resumed"
        private const val RESUME_STATUS_COMPLETED = "completed"
        private const val UPLOAD_STATUS_UPLOADING = "uploading"
        private const val UPLOAD_STATUS_UPLOADED = "uploaded"
        private const val UPLOAD_STATUS_FAILED = "failed"
        private const val SESSION_STATUS_PROCESSING = "processing"
        private const val MEDIA_TYPE_VIDEO = "video"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val BASE_RETRY_DELAY_MILLIS = 30_000L
        private const val MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1_000L
        private const val MAX_RETRY_EXPONENT = 10

        private val COMPLETE_READY_SESSION_STATUSES = setOf(
            UPLOAD_STATUS_UPLOADED,
            SESSION_STATUS_PROCESSING,
        )

        private val SESSION_RESET_HTTP_CODES = setOf(
            HTTP_FORBIDDEN,
            HTTP_NOT_FOUND,
            HTTP_CONFLICT,
        )
    }

    private enum class AssetUploadResult {
        SUCCESS,
        RETRY,
        AUTHENTICATION_REQUIRED,
    }

    private sealed interface SessionResolution {
        data class Uploaded(val assetId: String) : SessionResolution

        data class Active(
            val session: UploadSessionDto,
            val uploadUrls: UploadUrlsDto,
        ) : SessionResolution
    }

    private class R2UploadException(val statusCode: Int) : IOException(
        "R2 upload failed with HTTP $statusCode",
    )

    private class SessionResetRequiredException(cause: HttpException) : IOException(
        "Upload session is no longer available",
        cause,
    )
}
