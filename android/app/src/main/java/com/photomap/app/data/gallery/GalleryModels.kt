package com.photomap.app.data.gallery

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class GalleryFilter(
    val mediaType: GalleryMediaType = GalleryMediaType.ALL,
    val favoriteOnly: Boolean = false,
    val archived: Boolean? = false,
    val trashed: Boolean = false,
    val city: String? = null,
    val from: String? = null,
    val to: String? = null,
)

enum class GallerySection {
    MAIN,
    ARCHIVE,
    TRASH,
}

enum class GalleryQuickFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    FAVORITES,
}

fun GalleryFilter.section(): GallerySection = when {
    trashed -> GallerySection.TRASH
    archived == true -> GallerySection.ARCHIVE
    else -> GallerySection.MAIN
}

fun GalleryFilter.quickFilter(): GalleryQuickFilter = when {
    favoriteOnly -> GalleryQuickFilter.FAVORITES
    mediaType == GalleryMediaType.IMAGE -> GalleryQuickFilter.PHOTOS
    mediaType == GalleryMediaType.VIDEO -> GalleryQuickFilter.VIDEOS
    else -> GalleryQuickFilter.ALL
}

enum class GalleryMediaType(val apiValue: String?) {
    ALL(null),
    IMAGE("image"),
    VIDEO("video"),
}

data class AssetUiModel(
    val id: String,
    val mediaType: String,
    val mimeType: String,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val takenAt: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val isFavorite: Boolean,
) {
    val isVideo: Boolean get() = mediaType == GalleryMediaType.VIDEO.apiValue

    val aspectRatio: Float
        get() = if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            1f
        }
}

sealed interface GalleryListItem {
    val stableKey: String

    data class DateHeader(val groupKey: String, val label: String) : GalleryListItem {
        override val stableKey: String = "date:$groupKey"
    }

    data class Asset(val value: AssetUiModel) : GalleryListItem {
        override val stableKey: String = "asset:${value.id}"
    }
}

fun timelineGroupKey(takenAt: String?, zoneId: ZoneId = ZoneId.systemDefault()): String =
    timelineDate(takenAt, zoneId)?.toString() ?: UNKNOWN_DATE_KEY

fun timelineHeaderLabel(
    takenAt: String?,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val date = timelineDate(takenAt, zoneId) ?: return "Date unknown"
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

private fun timelineDate(takenAt: String?, zoneId: ZoneId): LocalDate? = runCatching {
    takenAt?.let { Instant.parse(it).atZone(zoneId).toLocalDate() }
}.getOrNull()

private const val UNKNOWN_DATE_KEY = "unknown"
