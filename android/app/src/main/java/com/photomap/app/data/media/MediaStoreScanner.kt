package com.photomap.app.data.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.local.LocalAssetEntity
import com.photomap.app.data.local.SyncStatus

class MediaStoreScanner(
    private val context: Context,
    private val localAssetDao: LocalAssetDao,
) {
    suspend fun scan() {
        val volume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.VOLUME_EXTERNAL
        } else {
            "external"
        }
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.Video.VideoColumns.DURATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val assets = buildList {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val takenAtIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val rawMediaType = cursor.getInt(mediaTypeIndex)
                    val mediaType = if (rawMediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        "video"
                    } else {
                        "image"
                    }
                    val uri = ContentUris.withAppendedId(collection, id)
                    add(
                        LocalAssetEntity(
                            localAssetId = "$mediaType:$id",
                            uri = uri.toString(),
                            mediaType = mediaType,
                            mimeType = cursor.getString(mimeIndex) ?: defaultMimeType(mediaType),
                            displayName = cursor.getString(nameIndex) ?: "$mediaType-$id",
                            sizeBytes = cursor.getLong(sizeIndex),
                            width = cursor.nullableInt(widthIndex),
                            height = cursor.nullableInt(heightIndex),
                            durationMs = cursor.nullableLong(durationIndex),
                            takenAt = cursor.nullableLong(takenAtIndex),
                            modifiedAt = cursor.nullableLong(modifiedIndex)?.times(1000),
                            syncStatus = SyncStatus.PENDING,
                            remoteAssetId = null,
                            lastError = null,
                            lastSyncedAt = null,
                        ),
                    )
                }
            }
        }

        if (assets.isNotEmpty()) {
            localAssetDao.insertDiscovered(assets)
        }
    }

    private fun android.database.Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index).takeIf { it > 0 }

    private fun android.database.Cursor.nullableLong(index: Int): Long? =
        if (isNull(index)) null else getLong(index).takeIf { it > 0 }

    private fun defaultMimeType(mediaType: String): String =
        if (mediaType == "video") "video/mp4" else "image/jpeg"
}
