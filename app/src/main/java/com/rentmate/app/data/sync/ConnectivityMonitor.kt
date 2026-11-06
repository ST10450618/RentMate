package com.rentmate.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectivityMonitor"

/** RM-0019: tells the app whether it can reach the internet. An interface so tests can fake it. */
interface ConnectivityMonitor {
    /**
     * True while the device has a network that reaches the internet. Emits the
     * current value first, then each change.
     */
    val isOnline: Flow<Boolean>
}

/**
 * RM-0019: [ConnectivityMonitor] on top of [ConnectivityManager]. It follows the
 * default network and counts it as online only when it has
 * [NetworkCapabilities.NET_CAPABILITY_INTERNET] and
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED]. A Wi-Fi network without
 * internet access, or behind a captive portal, counts as offline.
 */
@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectivityMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            Log.w(TAG, "no ConnectivityManager, assuming online")
            trySend(true)
            awaitClose()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val online = capabilities.hasInternet()
                Log.d(TAG, "default network changed, online=$online")
                trySend(online)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "default network lost")
                trySend(false)
            }
        }

        trySend(manager.activeNetwork?.let(manager::getNetworkCapabilities)?.hasInternet() == true)
        val registered = try {
            manager.registerDefaultNetworkCallback(callback)
            true
        } catch (e: SecurityException) {
            // Missing ACCESS_NETWORK_STATE. Should not happen; the manifest asks for it.
            Log.e(TAG, "could not follow the network", e)
            false
        }
        awaitClose { if (registered) manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
