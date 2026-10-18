package com.echo.app.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.echo.app.domain.model.DiscoveredHost
import com.echo.app.domain.protocol.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Announces the host on the local network (mDNS) so clients can find it without typing an IP. */
class HostAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(port: Int) {
        stop()
        val info = NsdServiceInfo().apply {
            serviceName = "Echo-${Build.MODEL}"
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }
}

/** Finds hosts announced by [HostAdvertiser]. Collect the flow to start discovery, cancel to stop. */
@Suppress("DEPRECATION")
class HostDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    fun discover(): Flow<List<DiscoveredHost>> = callbackFlow {
        val found = ConcurrentHashMap<String, DiscoveredHost>()
        val resolveLock = Mutex() // NsdManager resolves one service at a time

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("_echosync")) return
                launch {
                    resolveLock.withLock {
                        resolve(info)?.let {
                            found[info.serviceName] = it
                            trySend(found.values.toList())
                        }
                    }
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    private suspend fun resolve(info: NsdServiceInfo): DiscoveredHost? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val address = info.host?.hostAddress
                if (cont.isActive) {
                    cont.resume(address?.let { DiscoveredHost(info.serviceName.removePrefix("Echo-"), it, info.port) })
                }
            }
        })
    }
}
