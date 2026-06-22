package com.photomap.app.data.repository

import com.photomap.app.data.cache.OfflineImageCacheCoordinator
import com.photomap.app.data.gallery.GalleryRepository
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.preferences.BackendUrlConfiguration
import com.photomap.app.data.preferences.BackendUrlStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackendServerManager(
    private val backendUrlStore: BackendUrlStore,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val assetMutationQueue: AssetMutationQueue,
    private val galleryRepository: GalleryRepository,
    private val offlineImageCacheCoordinator: OfflineImageCacheCoordinator,
    private val localAssetDao: LocalAssetDao,
) {
    private val switchMutex = Mutex()

    val configuration: StateFlow<BackendUrlConfiguration> = backendUrlStore.configuration

    suspend fun switchServer(useCustomUrl: Boolean, customBaseUrl: String): Boolean =
        switchMutex.withLock {
            val nextConfiguration = backendUrlStore.preview(useCustomUrl, customBaseUrl)
            val serverChanged = nextConfiguration.effectiveBaseUrl !=
                backendUrlStore.configuration.value.effectiveBaseUrl
            if (!serverChanged) {
                backendUrlStore.save(nextConfiguration)
                return@withLock false
            }

            syncRepository.cancelAllSync()
            authRepository.logout()
            assetMutationQueue.clearAll()
            galleryRepository.clearRemoteReplica()
            offlineImageCacheCoordinator.clearForAccountChange()
            localAssetDao.resetForBackendChange()
            backendUrlStore.save(nextConfiguration)
            true
        }
}
