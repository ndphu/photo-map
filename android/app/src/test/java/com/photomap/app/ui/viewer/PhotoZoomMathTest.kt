package com.photomap.app.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoZoomMathTest {
    @Test
    fun oneFingerAtBaseScaleIsReservedForViewerPaging() {
        assertFalse(shouldHandlePhotoTransform(scale = 1f, pointerCount = 1, transformGestureActive = false))
        assertTrue(shouldHandlePhotoTransform(scale = 1f, pointerCount = 2, transformGestureActive = false))
        assertTrue(shouldHandlePhotoTransform(scale = 2f, pointerCount = 1, transformGestureActive = false))
    }

    private val container = Size(1000f, 600f)

    @Test
    fun scaleClampsToMinimumAndMaximum() {
        val maximum = applyPhotoGesture(PhotoTransform(), 10f, Offset.Zero, container)
        val minimum = applyPhotoGesture(maximum, 0.01f, Offset(100f, 100f), container)

        assertEquals(MAX_PHOTO_SCALE, maximum.scale)
        assertEquals(MIN_PHOTO_SCALE, minimum.scale)
        assertEquals(Offset.Zero, minimum.offset)
    }

    @Test
    fun doubleTapZoomsThenResets() {
        val zoomed = togglePhotoZoom(PhotoTransform(), container)
        val reset = togglePhotoZoom(zoomed, container)

        assertEquals(2.5f, zoomed.scale)
        assertEquals(PhotoTransform(), reset)
    }

    @Test
    fun panDoesNotApplyAtMinimumScale() {
        val transformed = applyPhotoGesture(
            transform = PhotoTransform(),
            zoomChange = 1f,
            panChange = Offset(500f, 500f),
            containerSize = container,
        )

        assertEquals(Offset.Zero, transformed.offset)
    }

    @Test
    fun offsetIsClampedInsideScaledContainer() {
        val clamped = clampPhotoOffset(Offset(10_000f, -10_000f), 2f, container)

        assertEquals(500f, clamped.x)
        assertEquals(-300f, clamped.y)
    }

    @Test
    fun contentChangeResetsTransform() {
        val zoomed = PhotoTransform(3f, Offset(100f, 80f))

        assertEquals(zoomed, resetTransformForContentChange("asset-a", "asset-a", zoomed))
        assertEquals(PhotoTransform(), resetTransformForContentChange("asset-a", "asset-b", zoomed))
        assertTrue(resetTransformForContentChange("asset-a", "asset-b", zoomed).offset == Offset.Zero)
    }
}
