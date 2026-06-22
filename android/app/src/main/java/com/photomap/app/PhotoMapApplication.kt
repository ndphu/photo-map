package com.photomap.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

class PhotoMapApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val isLoggedIn = container.authRepository.isLoggedIn()
        container.syncRepository.restoreBackgroundSync(isLoggedIn)
        container.offlineImageCacheCoordinator.restore(isLoggedIn)
        container.assetMetadataBackfillCoordinator.restore(isLoggedIn)
        if (isLoggedIn) container.assetMutationQueue.enqueueWork()
    }

    override fun newImageLoader(context: Context): ImageLoader = container.imageCacheManager.imageLoader
}
