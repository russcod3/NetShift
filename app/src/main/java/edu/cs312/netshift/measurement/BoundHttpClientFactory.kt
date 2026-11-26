package edu.cs312.netshift.measurement

import android.net.Network
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * Builds a client whose DNS lookups and sockets both use one selected [Network]. Binding only the
 * sockets would still resolve names on the default network. Never uses bindProcessToNetwork, so other
 * app traffic is unaffected. A fresh client and pool per Network; never reused across tests.
 */
object BoundHttpClientFactory {
    fun create(network: Network): OkHttpClient = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
        })
        .proxy(Proxy.NO_PROXY)
        .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cache(null)
        .build()
}

/** Call only on the base client returned by a lease (derived clients share its dispatcher and pool). */
fun OkHttpClient.releaseResources() {
    dispatcher.executorService.shutdown()
    connectionPool.evictAll()
}
