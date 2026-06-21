package com.photomap.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ParallelUploadSettingsTest {
    @Test
    fun presetsIncludeRequestedConcurrencyLevels() {
        assertEquals(listOf(8, 16, 32, 64, 128), SyncSettingsStore.PARALLEL_UPLOAD_PRESETS)
        assertEquals(8, SyncSettingsStore.DEFAULT_MAX_PARALLEL_UPLOADS)
    }

    @Test
    fun storedValuesNormalizeToNearestPreset() {
        assertEquals(8, normalizeParallelUploads(1))
        assertEquals(8, normalizeParallelUploads(12))
        assertEquals(16, normalizeParallelUploads(17))
        assertEquals(64, normalizeParallelUploads(80))
        assertEquals(128, normalizeParallelUploads(120))
        assertEquals(128, normalizeParallelUploads(200))
    }
}
