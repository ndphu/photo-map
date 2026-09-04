package com.photomap.app.data.repository

import com.photomap.app.data.cache.OfflineImageCacheCoordinator
import com.photomap.app.data.gallery.GalleryRepository
import com.photomap.app.data.local.LocalAssetDao
import com.photomap.app.data.preferences.CachedAccountStore
import com.photomap.app.data.preferences.shouldResetCachedAccount
import com.photomap.app.data.security.SecureTokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AccountDataCoordinator(
    private val tokenStore: SecureTokenStore,
    private val cachedAccountStore: CachedAccountStore,
    private val syncRepository: SyncRepository,
    private val assetMutationQueue: AssetMutationQueue,
    private val galleryRepository: GalleryRepository,
    private val offlineImageCacheCoordinator: OfflineImageCacheCoordinator,
    private val localAssetDao: LocalAssetDao,
) {
    private val accountChangeMutex = Mutex()

    suspend fun activateAccount(userId: String) = accountChangeMutex.withLock {
        if (!shouldResetCachedAccount(cachedAccountStore.cachedUserId(), userId)) return@withLock

        syncRepository.cancelAllSyncAndWait()
        clearCachedAccountData()
        cachedAccountStore.save(userId)
    }

    suspend fun logout() = accountChangeMutex.withLock {
        tokenStore.clear()
        syncRepository.cancelAllSyncAndWait()
    }

    suspend fun clearForBackendChange() = accountChangeMutex.withLock {
        tokenStore.clear()
        syncRepository.cancelAllSyncAndWait()
        clearCachedAccountData()
        cachedAccountStore.clear()
    }

    private suspend fun clearCachedAccountData() {
        assetMutationQueue.clearAll()
        galleryRepository.clearRemoteReplica()
        offlineImageCacheCoordinator.clearForAccountChange()
        localAssetDao.resetForAccountChange()
    }
}
