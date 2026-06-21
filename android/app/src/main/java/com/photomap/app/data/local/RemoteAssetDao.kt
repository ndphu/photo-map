package com.photomap.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteAssetDao {
    @Query(
        """
        SELECT * FROM remote_assets
        WHERE is_trashed = :trashed
          AND (:mediaType IS NULL OR media_type = :mediaType)
          AND (:favorite IS NULL OR is_favorite = :favorite)
          AND (:archived IS NULL OR is_archived = :archived)
          AND (:city IS NULL OR city = :city)
          AND (:fromDate IS NULL OR taken_at >= :fromDate)
          AND (:toDate IS NULL OR taken_at <= :toDate)
        ORDER BY CASE WHEN taken_at IS NULL THEN 1 ELSE 0 END, taken_at DESC, id DESC
        """,
    )
    fun observeGalleryAssets(
        mediaType: String?,
        favorite: Boolean?,
        archived: Boolean?,
        trashed: Boolean = false,
        city: String?,
        fromDate: String?,
        toDate: String?,
    ): Flow<List<RemoteAssetEntity>>

    @Query(
        """
        SELECT * FROM remote_assets
        WHERE is_trashed = 0
        ORDER BY CASE WHEN taken_at IS NULL THEN 1 ELSE 0 END, taken_at DESC, id DESC
        """,
    )
    fun observeViewerAssets(): Flow<List<RemoteAssetEntity>>

    @Query(
        """
        SELECT * FROM remote_assets
        WHERE is_trashed = 0
        ORDER BY CASE WHEN taken_at IS NULL THEN 0 ELSE 1 END, taken_at ASC, id ASC
        """,
    )
    suspend fun listOfflineCacheCandidates(): List<RemoteAssetEntity>

    @Query("SELECT * FROM remote_assets WHERE id = :id")
    fun observeAsset(id: String): Flow<RemoteAssetEntity?>

    @Query("SELECT * FROM remote_assets WHERE id = :id")
    suspend fun getAsset(id: String): RemoteAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RemoteAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RemoteAssetEntity>)

    @Query("DELETE FROM remote_assets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE remote_assets SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun markFavoriteLocal(id: String, isFavorite: Boolean)

    @Query("UPDATE remote_assets SET is_archived = :isArchived WHERE id = :id")
    suspend fun markArchivedLocal(id: String, isArchived: Boolean)

    @Query("UPDATE remote_assets SET is_trashed = :isTrashed WHERE id = :id")
    suspend fun markTrashedLocal(id: String, isTrashed: Boolean)

    @Query(
        """
        UPDATE remote_assets
        SET thumbnail_url = :thumbnailUrl,
            signed_url_updated_at = :updatedAt,
            signed_url_expires_at = :expiresAt
        WHERE id = :id
        """,
    )
    suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String, updatedAt: Long, expiresAt: Long?)

    @Query(
        """
        UPDATE remote_assets
        SET preview_url = :previewUrl,
            signed_url_updated_at = :updatedAt,
            signed_url_expires_at = :expiresAt
        WHERE id = :id
        """,
    )
    suspend fun updatePreviewUrl(id: String, previewUrl: String, updatedAt: Long, expiresAt: Long?)

    @Query(
        """
        UPDATE remote_assets
        SET poster_frame_url = :posterFrameUrl,
            signed_url_updated_at = :updatedAt,
            signed_url_expires_at = :expiresAt
        WHERE id = :id
        """,
    )
    suspend fun updatePosterFrameUrl(id: String, posterFrameUrl: String, updatedAt: Long, expiresAt: Long?)

    @Query("DELETE FROM remote_assets")
    suspend fun clearAllRemoteAssets()
}
