package edu.cs312.netshift.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import edu.cs312.netshift.measurement.BoundHttpClientFactory
import edu.cs312.netshift.model.FailureReason
import edu.cs312.netshift.model.TestTransport
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Requests a specific transport with ConnectivityManager.requestNetwork so a test can use cellular even
 * while Wi-Fi is the default. It cannot enable a disabled radio, grant a data subscription, or bypass an
 * administrator policy; it never toggles Wi-Fi or mobile data. Needs CHANGE_NETWORK_STATE.
 */
class AndroidNetworkLeaseProvider(context: Context) : NetworkLeaseProvider {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override suspend fun acquire(transport: TestTransport, timeoutMs: Long): LeaseResult {
        val cm = manager ?: return LeaseResult.Failed(FailureReason.NETWORK_UNAVAILABLE)
        if (timeoutMs <= 0) return LeaseResult.Failed(FailureReason.NETWORK_UNAVAILABLE)
        val lease = AndroidLease(cm, transport)
        var handedOver = false
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addTransportType(
                    if (transport == TestTransport.WIFI) NetworkCapabilities.TRANSPORT_WIFI
                    else NetworkCapabilities.TRANSPORT_CELLULAR,
                )
                .build()
            try {
                cm.requestNetwork(request, lease, timeoutMs.toInt())
            } catch (e: SecurityException) {
                return LeaseResult.Failed(FailureReason.PERMISSION_DENIED)
            } catch (e: RuntimeException) {
                // e.g. too many outstanding requests
                return LeaseResult.Failed(FailureReason.NETWORK_UNAVAILABLE)
            }
            val ready = withTimeoutOrNull(timeoutMs) { lease.ready.await() } ?: false
            if (!ready) return LeaseResult.Failed(FailureReason.NETWORK_UNAVAILABLE)
            lease.rejectionReason()?.let { return LeaseResult.Failed(it) }
            handedOver = true
            return LeaseResult.Acquired(lease)
        } finally {
            if (!handedOver) lease.release()
        }
    }
}

private class AndroidLease(
    private val manager: ConnectivityManager,
    override val transport: TestTransport,
) : ConnectivityManager.NetworkCallback(), NetworkLease {

    @Volatile private var network: Network? = null
    @Volatile private var capabilities: NetworkCapabilities? = null
    @Volatile private var properties: LinkProperties? = null
    @Volatile private var established = false
    @Volatile override var ipAddresses: List<String> = emptyList()
        private set

    /** true once capabilities and LinkProperties arrived; false if the request became unavailable. */
    val ready = CompletableDeferred<Boolean>()
    private val _interruption = MutableStateFlow<String?>(null)
    override val interruption: StateFlow<String?> = _interruption
    private val released = AtomicBoolean(false)

    override val handle: Long? get() = network?.networkHandle
    override val isMetered: Boolean?
        get() = capabilities?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
    override val isValidated: Boolean
        get() = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    override fun onAvailable(network: Network) {
        if (established && this.network != network) {
            interrupt()
            return
        }
        this.network = network
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        if (network != this.network) return
        capabilities = networkCapabilities
        if (established && !matchesTransport(networkCapabilities)) interrupt()
        checkReady()
    }

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        if (network != this.network) return
        val addresses = IpAddressSelector.select(linkProperties)
        if (established && addresses != ipAddresses) interrupt()
        if (!established) ipAddresses = addresses
        properties = linkProperties
        checkReady()
    }

    override fun onLost(network: Network) {
        if (network == this.network) interrupt()
    }

    override fun onUnavailable() {
        ready.complete(false)
    }

    private fun checkReady() {
        if (!established && network != null && capabilities != null && properties != null) {
            established = true
            ready.complete(true)
        }
    }

    private fun interrupt() {
        _interruption.compareAndSet(null, FailureReason.NETWORK_CHANGED)
    }

    private fun matchesTransport(c: NetworkCapabilities): Boolean =
        !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && when (transport) {
            TestTransport.WIFI -> c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            TestTransport.CELLULAR -> c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

    /** Null when the acquired network is usable for a measurement. */
    fun rejectionReason(): String? {
        val c = capabilities ?: return FailureReason.NETWORK_UNAVAILABLE
        if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return FailureReason.CAPTIVE_PORTAL
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return FailureReason.UNSUPPORTED_VPN
        if (!matchesTransport(c)) return FailureReason.TRANSPORT_MISMATCH
        if (properties?.httpProxy != null) return FailureReason.UNSUPPORTED_PROXY
        return null
    }

    override fun newHttpClient(): OkHttpClient =
        BoundHttpClientFactory.create(requireNotNull(network) { "Lease has no network" })

    override fun release() {
        if (released.compareAndSet(false, true)) {
            try {
                manager.unregisterNetworkCallback(this)
            } catch (_: IllegalArgumentException) {
                // never registered, or already unregistered
            }
        }
    }
}
