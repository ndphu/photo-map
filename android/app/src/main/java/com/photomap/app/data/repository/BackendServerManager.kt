package com.photomap.app.data.repository

import com.photomap.app.data.preferences.BackendUrlConfiguration
import com.photomap.app.data.preferences.BackendUrlStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackendServerManager(
    private val backendUrlStore: BackendUrlStore,
    private val accountDataCoordinator: AccountDataCoordinator,
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

            accountDataCoordinator.clearForBackendChange()
            backendUrlStore.save(nextConfiguration)
            true
        }
}
