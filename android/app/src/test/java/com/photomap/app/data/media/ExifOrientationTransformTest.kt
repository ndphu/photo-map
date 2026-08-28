package com.photomap.app.data.media

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ExifOrientationTransformTest {
    @Test
    fun mapsEveryExifOrientation() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_UNDEFINED to ExifOrientationTransform(false, 0f),
            ExifInterface.ORIENTATION_NORMAL to ExifOrientationTransform(false, 0f),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to ExifOrientationTransform(true, 0f),
            ExifInterface.ORIENTATION_ROTATE_180 to ExifOrientationTransform(false, 180f),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to ExifOrientationTransform(true, 180f),
            ExifInterface.ORIENTATION_TRANSPOSE to ExifOrientationTransform(true, 270f),
            ExifInterface.ORIENTATION_ROTATE_90 to ExifOrientationTransform(false, 90f),
            ExifInterface.ORIENTATION_TRANSVERSE to ExifOrientationTransform(true, 90f),
            ExifInterface.ORIENTATION_ROTATE_270 to ExifOrientationTransform(false, 270f),
        )

        expected.forEach { (orientation, transform) ->
            assertEquals(transform, ExifOrientationTransform.fromOrientation(orientation))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsUnsupportedOrientation() {
        ExifOrientationTransform.fromOrientation(9)
    }
}
