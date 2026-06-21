package com.photomap.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GalleryColumnPreferences {
    val columnCount: StateFlow<Int>

    fun setColumnCount(value: Int)
}

class GalleryPreferencesStore(context: Context) : GalleryColumnPreferences {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _columnCount = MutableStateFlow(
        normalizeGalleryColumnCount(
            preferences.getInt(KEY_COLUMN_COUNT, DEFAULT_GALLERY_COLUMNS),
        ),
    )

    override val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    override fun setColumnCount(value: Int) {
        val normalized = normalizeGalleryColumnCount(value)
        preferences.edit().putInt(KEY_COLUMN_COUNT, normalized).apply()
        _columnCount.value = normalized
    }

    private companion object {
        const val FILE_NAME = "gallery_preferences"
        const val KEY_COLUMN_COUNT = "column_count"
    }
}

const val MIN_GALLERY_COLUMNS = 2
const val MAX_GALLERY_COLUMNS = 6
const val DEFAULT_GALLERY_COLUMNS = 3

fun normalizeGalleryColumnCount(value: Int): Int =
    value.coerceIn(MIN_GALLERY_COLUMNS, MAX_GALLERY_COLUMNS)
