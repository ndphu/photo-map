package com.photomap.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _maxParallelUploads = MutableStateFlow(readMaxParallelUploads())
    val maxParallelUploads: StateFlow<Int> = _maxParallelUploads.asStateFlow()

    private val _uploadsPaused = MutableStateFlow(
        preferences.getBoolean(KEY_UPLOADS_PAUSED, DEFAULT_UPLOADS_PAUSED),
    )
    val uploadsPaused: StateFlow<Boolean> = _uploadsPaused.asStateFlow()

    private val _backgroundSyncEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, DEFAULT_BACKGROUND_SYNC_ENABLED),
    )
    val backgroundSyncEnabled: StateFlow<Boolean> = _backgroundSyncEnabled.asStateFlow()

    private val _wifiOnly = MutableStateFlow(
        preferences.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY),
    )
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _includeVideos = MutableStateFlow(
        preferences.getBoolean(KEY_INCLUDE_VIDEOS, DEFAULT_INCLUDE_VIDEOS),
    )
    val includeVideos: StateFlow<Boolean> = _includeVideos.asStateFlow()

    private val _offlineImageCacheEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_OFFLINE_IMAGE_CACHE_ENABLED, DEFAULT_OFFLINE_IMAGE_CACHE_ENABLED),
    )
    val offlineImageCacheEnabled: StateFlow<Boolean> = _offlineImageCacheEnabled.asStateFlow()

    private val _imageCacheLimitMb = MutableStateFlow(
        normalizeImageCacheLimitMb(
            preferences.getInt(KEY_IMAGE_CACHE_LIMIT_MB, DEFAULT_IMAGE_CACHE_LIMIT_MB),
        ),
    )
    val imageCacheLimitMb: StateFlow<Int> = _imageCacheLimitMb.asStateFlow()

    fun currentMaxParallelUploads(): Int = _maxParallelUploads.value

    fun areUploadsPaused(): Boolean = _uploadsPaused.value

    fun isBackgroundSyncEnabled(): Boolean = _backgroundSyncEnabled.value

    fun isWifiOnly(): Boolean = _wifiOnly.value

    fun shouldIncludeVideos(): Boolean = _includeVideos.value

    fun isOfflineImageCacheEnabled(): Boolean = _offlineImageCacheEnabled.value

    fun currentImageCacheLimitMb(): Int = _imageCacheLimitMb.value

    fun setMaxParallelUploads(value: Int) {
        val normalized = normalizeParallelUploads(value)
        preferences.edit().putInt(KEY_MAX_PARALLEL_UPLOADS, normalized).apply()
        _maxParallelUploads.value = normalized
    }

    fun setUploadsPaused(paused: Boolean) {
        preferences.edit().putBoolean(KEY_UPLOADS_PAUSED, paused).apply()
        _uploadsPaused.value = paused
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled).apply()
        _backgroundSyncEnabled.value = enabled
    }

    fun setWifiOnly(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
    }

    fun setIncludeVideos(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_INCLUDE_VIDEOS, enabled).apply()
        _includeVideos.value = enabled
    }

    fun setOfflineImageCacheEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_OFFLINE_IMAGE_CACHE_ENABLED, enabled).apply()
        _offlineImageCacheEnabled.value = enabled
    }

    fun setImageCacheLimitMb(value: Int) {
        val normalized = normalizeImageCacheLimitMb(value)
        preferences.edit().putInt(KEY_IMAGE_CACHE_LIMIT_MB, normalized).apply()
        _imageCacheLimitMb.value = normalized
    }

    private fun readMaxParallelUploads(): Int = preferences
        .getInt(KEY_MAX_PARALLEL_UPLOADS, DEFAULT_MAX_PARALLEL_UPLOADS)
        .let(::normalizeParallelUploads)

    companion object {
        const val DEFAULT_MAX_PARALLEL_UPLOADS = 8
        const val DEFAULT_IMAGE_CACHE_LIMIT_MB = 1024
        val PARALLEL_UPLOAD_PRESETS = listOf(8, 16, 32, 64, 128)
        val IMAGE_CACHE_LIMIT_PRESETS_MB = listOf(256, 512, 1024, 2048)

        private const val FILE_NAME = "sync_settings"
        private const val KEY_MAX_PARALLEL_UPLOADS = "max_parallel_uploads"
        private const val KEY_UPLOADS_PAUSED = "uploads_paused"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_INCLUDE_VIDEOS = "include_videos"
        private const val KEY_OFFLINE_IMAGE_CACHE_ENABLED = "offline_image_cache_enabled"
        private const val KEY_IMAGE_CACHE_LIMIT_MB = "image_cache_limit_mb"
        private const val DEFAULT_BACKGROUND_SYNC_ENABLED = false
        private const val DEFAULT_UPLOADS_PAUSED = false
        private const val DEFAULT_WIFI_ONLY = true
        private const val DEFAULT_INCLUDE_VIDEOS = true
        const val DEFAULT_OFFLINE_IMAGE_CACHE_ENABLED = true
    }
}

fun normalizeParallelUploads(value: Int): Int =
    SyncSettingsStore.PARALLEL_UPLOAD_PRESETS.minBy { preset ->
        kotlin.math.abs(preset.toLong() - value.toLong())
    }

fun normalizeImageCacheLimitMb(value: Int): Int =
    SyncSettingsStore.IMAGE_CACHE_LIMIT_PRESETS_MB.minBy { preset ->
        kotlin.math.abs(preset.toLong() - value.toLong())
    }
