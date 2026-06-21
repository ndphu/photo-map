package com.photomap.app.data.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryGridZoomTest {
    @Test
    fun zoomPastUpperThresholdDecreasesColumns() {
        assertEquals(
            GalleryGridZoomAction.DECREASE_COLUMNS,
            galleryGridZoomAction(GALLERY_ZOOM_IN_THRESHOLD + 0.01f),
        )
    }

    @Test
    fun zoomPastLowerThresholdIncreasesColumns() {
        assertEquals(
            GalleryGridZoomAction.INCREASE_COLUMNS,
            galleryGridZoomAction(GALLERY_ZOOM_OUT_THRESHOLD - 0.01f),
        )
    }

    @Test
    fun smallScaleChangesDoNotResizeGrid() {
        assertNull(galleryGridZoomAction(1.05f))
        assertNull(galleryGridZoomAction(GALLERY_ZOOM_IN_THRESHOLD))
        assertNull(galleryGridZoomAction(GALLERY_ZOOM_OUT_THRESHOLD))
    }
}
