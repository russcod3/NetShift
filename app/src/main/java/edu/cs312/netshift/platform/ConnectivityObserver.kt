package edu.cs312.netshift.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import edu.cs312.netshift.model.NetworkSnapshot
import edu.cs312.netshift.model.NetworkTransport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Passive default-route observation. It never requests a radio or issues test traffic. */
class ConnectivityObserver(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        if (manager == null) {
            trySend(NetworkSnapshot(unavailableReason = "Network status is unavailable"))
            close()
            return@callbackFlow
        }

        var currentNetwork: Network? = null
        var snapshot = NetworkSnapshot()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentNetwork = network
                snapshot = NetworkSnapshot(networkHandle = network.networkHandle)
                trySend(snapshot)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != currentNetwork) return
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                    else -> NetworkTransport.OTHER
                }
                snapshot = snapshot.copy(
                    transport = transport,
                    isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                    isCaptivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                )
                trySend(snapshot)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network != currentNetwork) return
                val addresses = linkProperties.linkAddresses.map { it.address }
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress ||
                        it.isMulticastAddress || it.isAnyLocalAddress }
                    .mapNotNull { it.hostAddress?.substringBefore('%') }
                    .distinct()
                    .sortedWith(compareBy<String> { ':' in it }.thenBy { it })
                snapshot = snapshot.copy(ipAddresses = addresses)
                trySend(snapshot)
            }

            override fun onLost(network: Network) {
                if (network != currentNetwork) return
                currentNetwork = null
                snapshot = NetworkSnapshot(transport = NetworkTransport.NONE)
                trySend(snapshot)
            }
        }

        var registered = false
        try {
            if (manager.activeNetwork == null) {
                trySend(NetworkSnapshot(transport = NetworkTransport.NONE))
            }
            manager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (_: SecurityException) {
            trySend(NetworkSnapshot(unavailableReason = "Network status access is unavailable"))
            close()
        }

        awaitClose {
            if (registered) manager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
