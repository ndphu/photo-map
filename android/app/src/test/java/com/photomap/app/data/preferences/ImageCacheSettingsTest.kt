package com.photomap.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheSettingsTest {
    @Test
    fun defaultLimitIsOneGigabyte() {
        assertEquals(1024, SyncSettingsStore.DEFAULT_IMAGE_CACHE_LIMIT_MB)
        assertEquals(true, SyncSettingsStore.DEFAULT_OFFLINE_IMAGE_CACHE_ENABLED)
    }

    @Test
    fun limitNormalizesToNearestPreset() {
        assertEquals(256, normalizeImageCacheLimitMb(100))
        assertEquals(512, normalizeImageCacheLimitMb(600))
        assertEquals(1024, normalizeImageCacheLimitMb(1000))
        assertEquals(2048, normalizeImageCacheLimitMb(3000))
    }
}
