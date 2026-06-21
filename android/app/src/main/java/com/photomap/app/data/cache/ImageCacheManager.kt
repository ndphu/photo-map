package com.photomap.app.data.cache

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.photomap.app.data.preferences.SyncSettingsStore
import java.io.File
import okio.Path.Companion.toOkioPath

enum class CloudImageVariant(val cacheKeyValue: String) {
    THUMBNAIL("thumbnail"),
    PREVIEW("preview"),
    POSTER_FRAME("posterFrame"),
}

class ImageCacheManager(
    context: Context,
    private val settingsStore: SyncSettingsStore,
) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, CACHE_DIRECTORY_NAME)

    @Volatile
    private var currentLoader = buildImageLoader(settingsStore.currentImageCacheLimitMb())

    val imageLoader: ImageLoader
        get() = currentLoader

    @Synchronized
    fun reconfigure(limitMb: Int) {
        val normalized = normalizeCacheLimit(limitMb)
        if (normalized == settingsStore.currentImageCacheLimitMb()) return
        settingsStore.setImageCacheLimitMb(normalized)
        val previous = currentLoader
        previous.shutdown()
        val replacement = buildImageLoader(normalized)
        currentLoader = replacement
        SingletonImageLoader.setUnsafe(replacement)
    }

    @Synchronized
    fun clear() {
        currentLoader.memoryCache?.clear()
        currentLoader.diskCache?.clear()
    }

    fun cacheSizeBytes(): Long = cacheDirectory
        .walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)

    private fun buildImageLoader(limitMb: Int): ImageLoader = ImageLoader.Builder(appContext)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDirectory.toOkioPath())
                .maxSizeBytes(limitMb.toLong() * BYTES_PER_MEGABYTE)
                .build()
        }
        .build()

    private fun normalizeCacheLimit(value: Int): Int =
        com.photomap.app.data.preferences.normalizeImageCacheLimitMb(value)

    private companion object {
        const val CACHE_DIRECTORY_NAME = "cloud_image_cache"
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}

fun cloudImageCacheKey(assetId: String, variant: CloudImageVariant): String =
    "asset:$assetId:${variant.cacheKeyValue}"

fun cloudImageVariant(apiValue: String): CloudImageVariant =
    CloudImageVariant.entries.firstOrNull { it.cacheKeyValue == apiValue } ?: CloudImageVariant.PREVIEW

fun cloudImageRequest(
    context: Context,
    assetId: String,
    variant: CloudImageVariant,
    url: String,
    prefetch: Boolean = false,
): ImageRequest {
    val cacheKey = cloudImageCacheKey(assetId, variant)
    val builder = ImageRequest.Builder(context)
        .data(url)
        .diskCacheKey(cacheKey)
    if (prefetch) {
        builder
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(1, 1)
    }
    return builder.build()
}
