package com.photomap.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class MediaVariants(
    val thumbnail: ByteArray,
    val preview: ByteArray,
    val posterFrame: ByteArray?,
    val derivativeVersion: Int,
)

data class ExifOrientationTransform(
    val flipHorizontal: Boolean,
    val rotationDegrees: Float,
) {
    companion object {
        fun fromOrientation(orientation: Int): ExifOrientationTransform = when (orientation) {
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL,
            -> ExifOrientationTransform(flipHorizontal = false, rotationDegrees = 0f)

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                ExifOrientationTransform(flipHorizontal = true, rotationDegrees = 0f)

            ExifInterface.ORIENTATION_ROTATE_180 ->
                ExifOrientationTransform(flipHorizontal = false, rotationDegrees = 180f)

            ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                ExifOrientationTransform(flipHorizontal = true, rotationDegrees = 180f)

            ExifInterface.ORIENTATION_TRANSPOSE ->
                ExifOrientationTransform(flipHorizontal = true, rotationDegrees = 270f)

            ExifInterface.ORIENTATION_ROTATE_90 ->
                ExifOrientationTransform(flipHorizontal = false, rotationDegrees = 90f)

            ExifInterface.ORIENTATION_TRANSVERSE ->
                ExifOrientationTransform(flipHorizontal = true, rotationDegrees = 90f)

            ExifInterface.ORIENTATION_ROTATE_270 ->
                ExifOrientationTransform(flipHorizontal = false, rotationDegrees = 270f)

            else -> error("Unsupported EXIF orientation: $orientation")
        }
    }
}

class MediaVariantGenerator(private val context: Context) {
    fun generate(uri: Uri, mediaType: String): MediaVariants {
        val isImage = mediaType != MEDIA_TYPE_VIDEO
        val source = if (isImage) imageBitmap(uri) else videoFrame(uri)
        val thumbnail = encodeWebp(scale(source, 320))
        val preview = encodeWebp(scale(source, 1600))
        source.recycle()

        return MediaVariants(
            thumbnail = thumbnail,
            preview = preview,
            posterFrame = if (isImage) null else preview,
            derivativeVersion = if (isImage) NORMALIZED_DERIVATIVE_VERSION else LEGACY_DERIVATIVE_VERSION,
        )
    }

    private fun imageBitmap(uri: Uri): Bitmap {
        val transform = context.contentResolver.openInputStream(uri)?.use { input ->
            val orientation = ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
            ExifOrientationTransform.fromOrientation(orientation)
        } ?: error("Unable to read image orientation")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode image")
        return applyOrientation(decoded, transform)
    }

    private fun applyOrientation(source: Bitmap, transform: ExifOrientationTransform): Bitmap {
        if (!transform.flipHorizontal && transform.rotationDegrees == 0f) return source

        val matrix = Matrix().apply {
            if (transform.flipHorizontal) postScale(-1f, 1f)
            if (transform.rotationDegrees != 0f) postRotate(transform.rotationDegrees)
        }
        val normalized = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
        if (normalized !== source) source.recycle()
        return normalized
    }

    private fun videoFrame(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Unable to extract video frame")
        } finally {
            retriever.release()
        }
    }

    private fun scale(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encodeWebp(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, 85, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > target * 2) {
            sample *= 2
        }
        return sample
    }

    companion object {
        private const val MEDIA_TYPE_VIDEO = "video"
        private const val LEGACY_DERIVATIVE_VERSION = 1
        private const val NORMALIZED_DERIVATIVE_VERSION = 2
    }
}
