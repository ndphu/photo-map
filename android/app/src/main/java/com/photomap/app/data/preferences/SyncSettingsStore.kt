package com.photomap.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _maxParallelUploads = MutableStateFlow(readMaxParallelUploads())
    val maxParallelUploads: StateFlow<Int> = _maxParallelUploads.asStateFlow()

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

    fun currentMaxParallelUploads(): Int = _maxParallelUploads.value

    fun isBackgroundSyncEnabled(): Boolean = _backgroundSyncEnabled.value

    fun isWifiOnly(): Boolean = _wifiOnly.value

    fun shouldIncludeVideos(): Boolean = _includeVideos.value

    fun setMaxParallelUploads(value: Int) {
        val normalized = value.coerceIn(MIN_PARALLEL_UPLOADS, MAX_PARALLEL_UPLOADS)
        preferences.edit().putInt(KEY_MAX_PARALLEL_UPLOADS, normalized).apply()
        _maxParallelUploads.value = normalized
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

    private fun readMaxParallelUploads(): Int = preferences
        .getInt(KEY_MAX_PARALLEL_UPLOADS, DEFAULT_MAX_PARALLEL_UPLOADS)
        .coerceIn(MIN_PARALLEL_UPLOADS, MAX_PARALLEL_UPLOADS)

    companion object {
        const val MIN_PARALLEL_UPLOADS = 1
        const val MAX_PARALLEL_UPLOADS = 16
        const val DEFAULT_MAX_PARALLEL_UPLOADS = 8

        private const val FILE_NAME = "sync_settings"
        private const val KEY_MAX_PARALLEL_UPLOADS = "max_parallel_uploads"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_INCLUDE_VIDEOS = "include_videos"
        private const val DEFAULT_BACKGROUND_SYNC_ENABLED = true
        private const val DEFAULT_WIFI_ONLY = true
        private const val DEFAULT_INCLUDE_VIDEOS = true
    }
}
