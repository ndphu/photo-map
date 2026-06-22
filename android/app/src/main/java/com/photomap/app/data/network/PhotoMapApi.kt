package com.photomap.app.data.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT

interface PhotoMapApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): DeviceDto

    @POST("upload-sessions")
    suspend fun createUploadSession(@Body request: CreateUploadSessionRequest): UploadSessionResponse

    @POST("upload-sessions/{id}/resume")
    suspend fun resumeUploadSession(@Path("id") id: String): UploadSessionResponse

    @PATCH("upload-sessions/{id}/status")
    suspend fun updateUploadSessionStatus(
        @Path("id") id: String,
        @Body request: UpdateUploadSessionStatusRequest,
    ): UploadSessionStatusResponse

    @POST("upload-sessions/{id}/complete")
    suspend fun completeUpload(
        @Path("id") id: String,
        @Body request: CompleteUploadRequest,
    ): CompleteUploadResponse

    @GET("assets")
    suspend fun listAssets(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
        @Query("mediaType") mediaType: String? = null,
        @Query("favorite") favorite: Boolean? = null,
        @Query("archived") archived: Boolean? = null,
        @Query("trashed") trashed: Boolean? = null,
        @Query("city") city: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): AssetListResponse

    @GET("assets/changes")
    suspend fun getAssetChanges(
        @Query("cursor") cursor: Long,
        @Query("limit") limit: Int,
    ): AssetChangesResponseDto

    @GET("assets/{id}")
    suspend fun getAsset(@Path("id") id: String): AssetDetailDto

    @GET("assets/{id}/read-url")
    suspend fun getReadUrl(
        @Path("id") id: String,
        @Query("variant") variant: String,
    ): ReadUrlResponse

    @PATCH("assets/{id}/favorite")
    suspend fun updateFavorite(
        @Path("id") id: String,
        @Body request: FavoriteRequest,
    ): AssetDetailDto

    @PATCH("assets/{id}/archive")
    suspend fun updateArchive(
        @Path("id") id: String,
        @Body request: ArchiveRequest,
    ): AssetDetailDto

    @PUT("assets/{id}/metadata")
    suspend fun replaceAssetMetadata(
        @Path("id") id: String,
        @Body request: ReplaceAssetMetadataRequest,
    ): AssetDetailDto

    @POST("assets/{id}/trash")
    suspend fun trashAsset(@Path("id") id: String): AssetDetailDto

    @POST("assets/{id}/restore")
    suspend fun restoreAsset(@Path("id") id: String): AssetDetailDto

    @DELETE("assets/{id}")
    suspend fun deleteAsset(@Path("id") id: String)

    @GET("search")
    suspend fun searchAssets(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): AssetListResponse

    @POST("albums")
    suspend fun createAlbum(@Body request: CreateAlbumRequest): AlbumDto

    @GET("albums")
    suspend fun listAlbums(): AlbumListResponse

    @GET("albums/{id}")
    suspend fun getAlbum(@Path("id") id: String): AlbumDto

    @PATCH("albums/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Body request: UpdateAlbumRequest,
    ): AlbumDto

    @DELETE("albums/{id}")
    suspend fun deleteAlbum(@Path("id") id: String)

    @POST("albums/{id}/assets")
    suspend fun addAssetToAlbum(
        @Path("id") id: String,
        @Body request: AddAssetToAlbumRequest,
    )

    @DELETE("albums/{id}/assets/{assetId}")
    suspend fun removeAssetFromAlbum(
        @Path("id") id: String,
        @Path("assetId") assetId: String,
    )

    @GET("albums/{id}/assets")
    suspend fun listAlbumAssets(@Path("id") id: String): AssetListResponse
}
