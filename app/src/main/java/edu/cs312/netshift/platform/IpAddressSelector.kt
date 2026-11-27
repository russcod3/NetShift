package edu.cs312.netshift.platform

import android.net.LinkProperties

/** Canonical assigned-address list: no loopback, link-local, multicast or unspecified; IPv4 first, sorted. */
object IpAddressSelector {
    fun select(properties: LinkProperties): List<String> = properties.linkAddresses.map { it.address }
        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isMulticastAddress || it.isAnyLocalAddress }
        .mapNotNull { it.hostAddress?.substringBefore('%') }
        .distinct()
        .sortedWith(compareBy<String> { ':' in it }.thenBy { it })
}
