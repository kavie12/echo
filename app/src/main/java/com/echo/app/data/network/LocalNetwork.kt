package com.echo.app.data.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

object LocalNetwork {
    /** Private IPv4 addresses of this phone (Wi-Fi or hotspot interface). */
    fun ipv4Addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}

object PairingCode {
    private val random = SecureRandom()
    fun generate(): String = "%06d".format(random.nextInt(1_000_000))
}
