package com.photomap.app

import android.content.Context
import androidx.room.Room
import com.photomap.app.data.local.PhotoMapDatabase
import com.photomap.app.data.local.MIGRATION_1_2
import com.photomap.app.data.gallery.GalleryRepository
import com.photomap.app.data.gallery.GalleryInvalidator
import com.photomap.app.data.gallery.GalleryBatchService
import com.photomap.app.data.gallery.RetrofitAssetsRemoteDataSource
import com.photomap.app.data.albums.AlbumRepository
import com.photomap.app.data.media.MediaMetadataExtractor
import com.photomap.app.data.media.MediaStoreScanner
import com.photomap.app.data.media.MediaVariantGenerator
import com.photomap.app.data.network.ApiFactory
import com.photomap.app.data.network.ConnectivityObserver
import com.photomap.app.data.search.RetrofitSearchRemoteDataSource
import com.photomap.app.data.search.SearchRepository
import com.photomap.app.data.preferences.SyncSettingsStore
import com.photomap.app.data.repository.AssetRepository
import com.photomap.app.data.repository.RetrofitAssetRemoteDataSource
import com.photomap.app.data.repository.AuthRepository
import com.photomap.app.data.repository.SyncRepository
import com.photomap.app.data.security.SecureTokenStore
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore = SecureTokenStore(appContext)
    val syncSettingsStore = SyncSettingsStore(appContext)
    private val apiAndClient = ApiFactory.create(tokenStore)
    val api = apiAndClient.first
    val uploadClient = OkHttpClient()

    val database = Room.databaseBuilder(
        appContext,
        PhotoMapDatabase::class.java,
        "photo-map.db",
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    val mediaScanner = MediaStoreScanner(appContext, database.localAssetDao())
    val metadataExtractor = MediaMetadataExtractor(appContext)
    val variantGenerator = MediaVariantGenerator(appContext)

    private val galleryInvalidator = GalleryInvalidator()
    val authRepository = AuthRepository(appContext, api, tokenStore)
    val assetRepository = AssetRepository(RetrofitAssetRemoteDataSource(api), galleryInvalidator)
    val galleryRepository = GalleryRepository(RetrofitAssetsRemoteDataSource(api), galleryInvalidator)
    val searchRepository = SearchRepository(RetrofitSearchRemoteDataSource(api))
    val albumRepository = AlbumRepository(api)
    val galleryBatchService = GalleryBatchService(assetRepository, albumRepository)
    val connectivityObserver = ConnectivityObserver(appContext)
    val syncRepository = SyncRepository(
        appContext,
        mediaScanner,
        database.localAssetDao(),
        syncSettingsStore,
    )
}
