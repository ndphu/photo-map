package com.photomap.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class OriginalTransferProgress(
    val transferredBytes: Long,
    val totalBytes: Long?,
)

interface OriginalImageStore {
    suspend fun prepareOriginal(
        assetId: String,
        originalFilename: String?,
        onProgress: (OriginalTransferProgress) -> Unit,
    ): File

    suspend fun saveOriginal(
        assetId: String,
        destination: Uri,
        onProgress: (OriginalTransferProgress) -> Unit,
    )

    fun deletePreparedOriginal(assetId: String)
    fun clearPreparedOriginals()
}

class OriginalImageService(
    context: Context,
    private val assetRepository: AssetRepository,
    private val client: OkHttpClient,
) : OriginalImageStore {
    private val contentResolver: ContentResolver = context.contentResolver
    private val originalDirectory = File(context.cacheDir, ORIGINAL_DIRECTORY_NAME)

    init {
        clearPreparedOriginals()
    }

    override suspend fun prepareOriginal(
        assetId: String,
        originalFilename: String?,
        onProgress: (OriginalTransferProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        originalDirectory.mkdirs()
        deletePreparedOriginal(assetId)
        val destination = File(originalDirectory, preparedFileName(assetId, originalFilename))
        try {
            transferWithSignedUrlRetry(assetId, onProgress) { responseBody ->
                destination.outputStream().buffered().use { output ->
                    copyResponse(responseBody.byteStream(), output, responseBody.contentLength(), onProgress)
                }
            }
            destination
        } catch (error: CancellationException) {
            destination.delete()
            throw error
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    override suspend fun saveOriginal(
        assetId: String,
        destination: Uri,
        onProgress: (OriginalTransferProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val prepared = preparedOriginal(assetId)
            contentResolver.openOutputStream(destination, "w")?.buffered().use { output ->
                requireNotNull(output) { "Cannot open download destination" }
                if (prepared != null) {
                    prepared.inputStream().buffered().use { input ->
                        copyResponse(input, output, prepared.length(), onProgress)
                    }
                } else {
                    transferWithSignedUrlRetry(assetId, onProgress) { responseBody ->
                        copyResponse(responseBody.byteStream(), output, responseBody.contentLength(), onProgress)
                    }
                }
            }
        } catch (error: CancellationException) {
            runCatching { contentResolver.delete(destination, null, null) }
            throw error
        } catch (error: Exception) {
            runCatching { contentResolver.delete(destination, null, null) }
            throw error
        }
    }

    override fun deletePreparedOriginal(assetId: String) {
        originalDirectory.listFiles()
            ?.filter { it.name.startsWith("$assetId.") }
            ?.forEach(File::delete)
    }

    override fun clearPreparedOriginals() {
        originalDirectory.listFiles()?.forEach(File::delete)
    }

    private suspend fun transferWithSignedUrlRetry(
        assetId: String,
        onProgress: (OriginalTransferProgress) -> Unit,
        consume: suspend (okhttp3.ResponseBody) -> Unit,
    ) {
        var attempt = 0
        while (attempt < MAX_SIGNED_URL_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val signedUrl = assetRepository.originalUrl(assetId)
            val request = Request.Builder().url(signedUrl).get().build()
            val call = client.newCall(request)
            val response = try {
                runInterruptible { call.execute() }
            } catch (error: IOException) {
                throw error
            }
            response.use {
                if (response.code == HTTP_FORBIDDEN && attempt == 0) {
                    attempt += 1
                    return@use
                }
                if (!response.isSuccessful) {
                    throw OriginalDownloadException(response.code)
                }
                val body = response.body ?: throw IOException("Original response body is empty")
                consume(body)
                return
            }
        }
        throw OriginalDownloadException(HTTP_FORBIDDEN)
    }

    private suspend fun copyResponse(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        contentLength: Long,
        onProgress: (OriginalTransferProgress) -> Unit,
    ) {
        val total = contentLength.takeIf { it >= 0L }
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var transferred = 0L
        onProgress(OriginalTransferProgress(0L, total))
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            transferred += count
            onProgress(OriginalTransferProgress(transferred, total))
        }
        output.flush()
    }

    private fun preparedOriginal(assetId: String): File? = originalDirectory.listFiles()
        ?.firstOrNull { it.name.startsWith("$assetId.") && it.isFile && it.length() > 0L }

    private fun preparedFileName(assetId: String, originalFilename: String?): String {
        val extension = originalFilename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(SAFE_EXTENSION) }
            ?: DEFAULT_EXTENSION
        return "$assetId.$extension"
    }

    private companion object {
        const val ORIGINAL_DIRECTORY_NAME = "original_view"
        const val DEFAULT_EXTENSION = "img"
        const val MAX_SIGNED_URL_ATTEMPTS = 2
        const val HTTP_FORBIDDEN = 403
        const val COPY_BUFFER_SIZE = 64 * 1024
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,10}")
    }
}

class OriginalDownloadException(val statusCode: Int) : IOException("Cannot download original")
