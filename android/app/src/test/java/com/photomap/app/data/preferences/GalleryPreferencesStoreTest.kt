package com.photomap.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryPreferencesStoreTest {
    @Test
    fun defaultIsThreeColumns() {
        assertEquals(3, DEFAULT_GALLERY_COLUMNS)
    }

    @Test
    fun columnCountIsClampedToSupportedRange() {
        assertEquals(MIN_GALLERY_COLUMNS, normalizeGalleryColumnCount(1))
        assertEquals(4, normalizeGalleryColumnCount(4))
        assertEquals(MAX_GALLERY_COLUMNS, normalizeGalleryColumnCount(7))
    }
}
