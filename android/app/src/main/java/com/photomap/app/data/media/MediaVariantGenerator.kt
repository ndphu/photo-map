package com.photomap.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class MediaVariants(
    val thumbnail: ByteArray,
    val preview: ByteArray,
    val posterFrame: ByteArray?,
)

class MediaVariantGenerator(private val context: Context) {
    fun generate(uri: Uri, mediaType: String): MediaVariants {
        val source = if (mediaType == "video") videoFrame(uri) else imageBitmap(uri)
        val thumbnail = encodeWebp(scale(source, 320))
        val preview = encodeWebp(scale(source, 1600))
        source.recycle()

        return MediaVariants(
            thumbnail = thumbnail,
            preview = preview,
            posterFrame = if (mediaType == "video") preview else null,
        )
    }

    private fun imageBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode image")
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
}
