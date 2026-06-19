package com.photomap.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photomap.app.PhotoMapApplication
import com.photomap.app.data.local.LocalAssetEntity
import com.photomap.app.data.local.SyncStatus
import com.photomap.app.data.network.CompleteUploadRequest
import com.photomap.app.data.network.CreateUploadSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.security.MessageDigest
import java.time.Instant

class MediaSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container = (appContext.applicationContext as PhotoMapApplication).container
    private val dao = container.database.localAssetDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deviceId = container.tokenStore.deviceId() ?: return@withContext Result.failure()
        if (container.tokenStore.accessToken() == null) {
            return@withContext Result.failure()
        }

        val pending = dao.pending(listOf(SyncStatus.PENDING), MAX_ASSETS_PER_RUN)
        pending.forEach { asset ->
            runCatching { uploadAsset(asset, deviceId) }
                .onFailure { error ->
                    dao.updateStatus(
                        asset.localAssetId,
                        SyncStatus.FAILED,
                        error.message ?: error::class.java.simpleName,
                    )
                }
        }

        if (dao.pending(listOf(SyncStatus.PENDING), 1).isNotEmpty()) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun uploadAsset(asset: LocalAssetEntity, deviceId: String) {
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

    companion object {
        const val WORK_NAME = "media-sync"
        private const val MAX_ASSETS_PER_RUN = 20
    }
}
