package com.photomap.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkState {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

interface NetworkMonitor {
    val state: Flow<NetworkState>
}

class ConnectivityObserver(context: Context) : NetworkMonitor {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override val state: Flow<NetworkState> = callbackFlow {
        fun publishCurrentState() {
            trySend(connectivityManager.currentNetworkState())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishCurrentState()

            override fun onLost(network: Network) = publishCurrentState()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = publishCurrentState()
        }

        publishCurrentState()
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

private fun ConnectivityManager.currentNetworkState(): NetworkState {
    val network = activeNetwork ?: return NetworkState.OFFLINE
    val capabilities = getNetworkCapabilities(network) ?: return NetworkState.UNKNOWN
    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return if (hasInternet && validated) NetworkState.ONLINE else NetworkState.OFFLINE
}
