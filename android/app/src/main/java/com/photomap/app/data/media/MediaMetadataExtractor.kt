package com.photomap.app.data.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.photomap.app.data.local.LocalAssetEntity
import java.time.Instant
import java.time.ZoneId

data class ExtractedMetadata(
    val takenAt: String?,
    val takenAtSource: String?,
    val timezoneOffsetMinutes: Int?,
    val orientation: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val cameraMake: String?,
    val cameraModel: String?,
    val software: String?,
)

class MediaMetadataExtractor(private val context: Context) {
    fun extract(asset: LocalAssetEntity): ExtractedMetadata {
        if (asset.mediaType != "image") {
            return baseMetadata(asset)
        }

        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(asset.uri))?.use { stream ->
                val exif = ExifInterface(stream)
                val coordinates = exif.latLong
                val exifTakenAt = exif.getDateTimeOriginal()
                ExtractedMetadata(
                    takenAt = exifTakenAt?.let(Instant::ofEpochMilli)?.toString()
                        ?: asset.takenAt?.let(Instant::ofEpochMilli)?.toString(),
                    takenAtSource = if (exifTakenAt != null) "exif" else "media_store",
                    timezoneOffsetMinutes = timezoneOffsetMinutes(exifTakenAt ?: asset.takenAt),
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1),
                    latitude = coordinates?.getOrNull(0),
                    longitude = coordinates?.getOrNull(1),
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
                )
            } ?: baseMetadata(asset)
        }.getOrElse { baseMetadata(asset) }
    }

    private fun baseMetadata(asset: LocalAssetEntity): ExtractedMetadata = ExtractedMetadata(
        takenAt = asset.takenAt?.let(Instant::ofEpochMilli)?.toString(),
        takenAtSource = asset.takenAt?.let { "media_store" },
        timezoneOffsetMinutes = timezoneOffsetMinutes(asset.takenAt),
        orientation = null,
        latitude = null,
        longitude = null,
        cameraMake = null,
        cameraModel = null,
        software = null,
    )

    private fun timezoneOffsetMinutes(timeMillis: Long?): Int? {
        timeMillis ?: return null
        return ZoneId.systemDefault().rules
            .getOffset(Instant.ofEpochMilli(timeMillis))
            .totalSeconds / 60
    }
}
