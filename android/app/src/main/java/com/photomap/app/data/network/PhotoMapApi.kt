package com.photomap.app.data.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PhotoMapApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): DeviceDto

    @POST("upload-sessions")
    suspend fun createUploadSession(@Body request: CreateUploadSessionRequest): UploadSessionResponse

    @POST("upload-sessions/{id}/complete")
    suspend fun completeUpload(
        @Path("id") id: String,
        @Body request: CompleteUploadRequest,
    ): CompleteUploadResponse

    @GET("assets")
    suspend fun listAssets(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): AssetListResponse

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

    @POST("assets/{id}/trash")
    suspend fun trashAsset(@Path("id") id: String): AssetDetailDto
}
