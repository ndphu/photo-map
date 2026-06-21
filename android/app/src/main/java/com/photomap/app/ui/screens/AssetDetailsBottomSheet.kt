package com.photomap.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photomap.app.data.gallery.AssetDetailModel
import com.photomap.app.data.cache.CloudImageVariant
import com.photomap.app.data.cache.cloudImageRequest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

data class AssetDetailsRowData(
    val label: String,
    val value: String,
    val copyValue: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailsBottomSheet(
    asset: AssetDetailModel,
    previewUrl: String?,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onOpenMaps: (latitude: Double, longitude: Double) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            AssetDetailsSummary(asset, previewUrl, onCopy)
            AssetDetailsSection("Date and time", dateTimeRows(asset), onCopy)
            AssetDetailsSection("Properties", propertyRows(asset), onCopy)
            AssetLocationSection(asset, onCopy, onOpenMaps)
            AssetCameraSection(asset, onCopy)
            AssetCloudInfoSection(asset, onCopy)
            AssetDetailsSection("Status", statusRows(asset), onCopy)
        }
    }
}

@Composable
private fun AssetDetailsSummary(
    asset: AssetDetailModel,
    previewUrl: String?,
    onCopy: (String, String) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (previewUrl != null) {
                AsyncImage(
                    model = cloudImageRequest(context, asset.id, CloudImageVariant.PREVIEW, previewUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Text(if (asset.mediaType == "video") "Video" else "Photo")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.originalFilename?.takeIf(String::isNotBlank) ?: "Unknown file name",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (asset.mediaType == "video") "Video" else "Photo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    AssetDetailsSection("Summary", summaryRows(asset), onCopy)
}

@Composable
fun AssetDetailsSection(
    title: String,
    rows: List<AssetDetailsRowData>,
    onCopy: (label: String, value: String) -> Unit,
) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        rows.forEach { row -> AssetDetailsRow(row, onCopy) }
    }
}

@Composable
fun AssetDetailsRow(
    row: AssetDetailsRowData,
    onCopy: (label: String, value: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
        )
        row.copyValue?.let { value ->
            IconButton(
                onClick = { onCopy(row.label, value) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy ${row.label}")
            }
        }
    }
}

@Composable
fun AssetLocationSection(
    asset: AssetDetailModel,
    onCopy: (label: String, value: String) -> Unit,
    onOpenMaps: (latitude: Double, longitude: Double) -> Unit,
) {
    val rows = locationRows(asset)
    if (rows.isEmpty()) return
    AssetDetailsSection("Location", rows, onCopy)
    val latitude = asset.latitude
    val longitude = asset.longitude
    if (latitude != null && longitude != null) {
        TextButton(
            onClick = { onOpenMaps(latitude, longitude) },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Outlined.Map, contentDescription = null)
            Text("Open in Maps", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun AssetCameraSection(asset: AssetDetailModel, onCopy: (String, String) -> Unit) {
    AssetDetailsSection("Camera and EXIF", cameraRows(asset), onCopy)
}

@Composable
fun AssetCloudInfoSection(asset: AssetDetailModel, onCopy: (String, String) -> Unit) {
    AssetDetailsSection("Cloud", cloudRows(asset), onCopy)
}

internal fun summaryRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOfNotNull(
    asset.originalFilename.nonBlankRow("File name", copy = true),
    AssetDetailsRowData("Media type", if (asset.mediaType == "video") "Video" else "Photo"),
    formatAssetDateTime(asset.takenAt)?.let { AssetDetailsRowData("Date", it) },
    formatResolution(asset.width, asset.height)?.let { AssetDetailsRowData("Resolution", it) },
    formatFileSize(asset.fileSizeBytes)?.let { AssetDetailsRowData("Size", it) },
    asset.durationMs?.takeIf { asset.mediaType == "video" }?.let {
        AssetDetailsRowData("Duration", formatDuration(it))
    },
    asset.mimeType.nonBlankRow("MIME type"),
)

internal fun dateTimeRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOfNotNull(
    formatAssetDateTime(asset.takenAt)?.let { AssetDetailsRowData("Taken", it) },
    asset.takenAtSource.nonBlankRow("Date source"),
    asset.timezoneOffsetMinutes?.let { AssetDetailsRowData("Time zone", formatTimezoneOffset(it)) },
    formatAssetDateTime(asset.uploadedAt)?.let { AssetDetailsRowData("Uploaded", it) },
    formatAssetDateTime(asset.createdAt)?.let { AssetDetailsRowData("Created", it) },
    formatAssetDateTime(asset.updatedAt)?.let { AssetDetailsRowData("Updated", it) },
    formatAssetDateTime(asset.trashedAt)?.let { AssetDetailsRowData("Trashed", it) },
)

internal fun propertyRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOfNotNull(
    formatResolution(asset.width, asset.height)?.let { AssetDetailsRowData("Resolution", it) },
    asset.orientation?.let { AssetDetailsRowData("Orientation", it.toString()) },
    asset.durationMs?.takeIf { asset.mediaType == "video" }?.let {
        AssetDetailsRowData("Duration", formatDuration(it))
    },
    asset.originalFilename.nonBlankRow("File name", copy = true),
    asset.mimeType.nonBlankRow("MIME type"),
    formatFileSize(asset.fileSizeBytes)?.let { AssetDetailsRowData("File size", it) },
)

internal fun locationRows(asset: AssetDetailModel): List<AssetDetailsRowData> {
    val coordinates = formatGps(asset.latitude, asset.longitude)
    return listOfNotNull(
        asset.placeName.nonBlankRow("Place"),
        asset.city.nonBlankRow("City"),
        asset.region.nonBlankRow("Region"),
        asset.country.nonBlankRow("Country"),
        coordinates?.let { AssetDetailsRowData("Coordinates", it, it) },
    )
}

internal fun cameraRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOfNotNull(
    asset.cameraMake.nonBlankRow("Make"),
    asset.cameraModel.nonBlankRow("Model"),
    asset.software.nonBlankRow("Software"),
    asset.orientation?.let { AssetDetailsRowData("Orientation", it.toString()) },
    asset.takenAtSource.nonBlankRow("Date source"),
)

internal fun cloudRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOfNotNull(
    AssetDetailsRowData("Asset ID", asset.id, asset.id),
    asset.checksumSha256.nonBlankRow("SHA-256", copy = true),
    formatAssetDateTime(asset.uploadedAt)?.let { AssetDetailsRowData("Uploaded", it) },
)

internal fun statusRows(asset: AssetDetailModel): List<AssetDetailsRowData> = listOf(
    AssetDetailsRowData("Favorite", yesNo(asset.isFavorite)),
    AssetDetailsRowData("Archived", yesNo(asset.isArchived)),
    AssetDetailsRowData("Trashed", yesNo(asset.isTrashed)),
    AssetDetailsRowData("Hidden", yesNo(asset.isHidden)),
)

fun formatFileSize(bytes: Long?): String? {
    if (bytes == null || bytes < 0) return null
    if (bytes < BYTES_PER_KILOBYTE) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / BYTES_PER_KILOBYTE
    var unitIndex = 0
    while (value >= BYTES_PER_KILOBYTE && unitIndex < units.lastIndex) {
        value /= BYTES_PER_KILOBYTE
        unitIndex += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

fun formatResolution(width: Int?, height: Int?): String? =
    if (width != null && width > 0 && height != null && height > 0) "$width × $height" else null

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

fun formatGps(latitude: Double?, longitude: Double?): String? =
    if (latitude != null && longitude != null) {
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    } else {
        null
    }

fun formatAssetDateTime(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val instant = runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrNull() ?: return null
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun String?.nonBlankRow(label: String, copy: Boolean = false): AssetDetailsRowData? =
    this?.takeIf(String::isNotBlank)?.let { AssetDetailsRowData(label, it, it.takeIf { copy }) }

private fun formatTimezoneOffset(minutes: Int): String {
    val sign = if (minutes >= 0) "+" else "-"
    val absolute = abs(minutes)
    return "UTC%s%02d:%02d".format(Locale.US, sign, absolute / 60, absolute % 60)
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private const val BYTES_PER_KILOBYTE = 1_024.0
