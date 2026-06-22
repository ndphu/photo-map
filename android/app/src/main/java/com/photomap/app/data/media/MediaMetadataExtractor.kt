package com.photomap.app.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.photomap.app.data.local.LocalAssetEntity
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    fun extract(asset: LocalAssetEntity): ExtractedMetadata =
        runCatching { extractForBackfill(asset) }.getOrElse { baseMetadata(asset) }

    fun extractForBackfill(asset: LocalAssetEntity): ExtractedMetadata = when (asset.mediaType) {
        "image" -> extractImage(asset)
        "video" -> extractVideo(asset)
        else -> baseMetadata(asset)
    }

    private fun extractImage(asset: LocalAssetEntity): ExtractedMetadata {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMediaLocationAccess(context)) {
            MediaStore.setRequireOriginal(Uri.parse(asset.uri))
        } else {
            Uri.parse(asset.uri)
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open local image")
        return stream.use {
            val exif = ExifInterface(stream)
            val coordinates = exif.latLong
            val exifTakenAt = exif.getDateTimeOriginal()
            ExtractedMetadata(
                takenAt = exifTakenAt?.let(Instant::ofEpochMilli)?.toString()
                    ?: asset.takenAt?.let(Instant::ofEpochMilli)?.toString(),
                takenAtSource = if (exifTakenAt != null) "exif" else asset.takenAt?.let { "media_store" },
                timezoneOffsetMinutes = timezoneOffsetMinutes(exifTakenAt ?: asset.takenAt),
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1),
                latitude = coordinates?.getOrNull(0),
                longitude = coordinates?.getOrNull(1),
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
            )
        }
    }

    private fun extractVideo(asset: LocalAssetEntity): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(asset.uri))
            val recordedAt = parseVideoDate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
            val coordinates = parseVideoLocation(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION),
            )
            return ExtractedMetadata(
                takenAt = recordedAt?.toString() ?: asset.takenAt?.let(Instant::ofEpochMilli)?.toString(),
                takenAtSource = if (recordedAt != null) "video_metadata" else asset.takenAt?.let { "media_store" },
                timezoneOffsetMinutes = timezoneOffsetMinutes(recordedAt?.toEpochMilli() ?: asset.takenAt),
                orientation = null,
                latitude = coordinates?.first,
                longitude = coordinates?.second,
                cameraMake = null,
                cameraModel = null,
                software = null,
            )
        } finally {
            retriever.release()
        }
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

fun hasMediaLocationAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

internal fun parseVideoLocation(value: String?): Pair<Double, Double>? {
    val match = value?.trim()?.let(VIDEO_LOCATION_PATTERN::matchEntire) ?: return null
    val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
    val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}

internal fun parseVideoDate(value: String?): Instant? {
    val date = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    runCatching { return Instant.parse(date) }
    VIDEO_DATE_FORMATTERS.forEach { formatter ->
        runCatching { return OffsetDateTime.parse(date, formatter).toInstant() }
    }
    return null
}

private val VIDEO_LOCATION_PATTERN = Regex("^([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)/?$")
private val VIDEO_DATE_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX"),
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
)
