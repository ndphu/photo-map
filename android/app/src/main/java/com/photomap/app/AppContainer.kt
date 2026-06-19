package com.photomap.app

import android.content.Context
import androidx.room.Room
import com.photomap.app.data.local.PhotoMapDatabase
import com.photomap.app.data.local.MIGRATION_1_2
import com.photomap.app.data.media.MediaMetadataExtractor
import com.photomap.app.data.media.MediaStoreScanner
import com.photomap.app.data.media.MediaVariantGenerator
import com.photomap.app.data.network.ApiFactory
import com.photomap.app.data.preferences.SyncSettingsStore
import com.photomap.app.data.repository.AssetRepository
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

    val authRepository = AuthRepository(appContext, api, tokenStore)
    val assetRepository = AssetRepository(api)
    val syncRepository = SyncRepository(
        appContext,
        mediaScanner,
        database.localAssetDao(),
        syncSettingsStore,
    )
}
