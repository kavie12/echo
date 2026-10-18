package com.echo.app.data.network

import android.os.Build
import android.util.Base64
import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.data.settings.AppSettings
import com.echo.app.domain.model.DeviceInfo
import com.echo.app.domain.model.PairedDevice
import com.echo.app.domain.model.RejectReason
import com.echo.app.domain.model.SyncCategory
import com.echo.app.domain.model.SyncPrefs
import com.echo.app.domain.protocol.Packet
import com.echo.app.domain.protocol.Protocol
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket server that runs on the host phone.
 * Handles pairing (code or token), tracks connected clients and pushes packets to them.
 */
class HostServer(private val bus: EventBus, private val settings: AppSettings) {

    private class Session(val device: PairedDevice, val socket: WebSocketSession) {
        @Volatile var prefs: SyncPrefs = SyncPrefs()
    }

    private sealed interface AuthResult {
        data class Accepted(val device: PairedDevice) : AuthResult
        data class Denied(val reason: RejectReason) : AuthResult
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val paired = MutableStateFlow<List<PairedDevice>>(emptyList())
    private val online = MutableStateFlow<Set<String>>(emptySet())
    private val _code = MutableStateFlow(PairingCode.generate())
    private val authLock = Mutex()
    private val limiter = AttemptLimiter()
    private var engine: ApplicationEngine? = null

    val code: StateFlow<String> = _code.asStateFlow()

    /** Every paired device with its current connectivity. */
    val devices: Flow<List<DeviceInfo>> = combine(paired, online) { list, on ->
        list.map { DeviceInfo(it.id, it.name, it.id in on) }
    }

    fun regenerateCode() {
        _code.value = PairingCode.generate()
    }

    fun connectedDeviceIds(): List<String> = sessions.keys.toList()

    suspend fun start() {
        if (engine != null) return
        paired.value = settings.pairedDevices.first()
        engine = embeddedServer(CIO, port = Protocol.PORT) {
            install(WebSockets) {
                pingPeriodMillis = 15_000
                timeoutMillis = 30_000
            }
            routing { webSocket(Protocol.PATH) { handle(this) } }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(300, 1_000)
        engine = null
        sessions.clear()
        online.value = emptySet()
        paired.value = emptyList()
    }

    // ---- Outgoing ----------------------------------------------------------------------------

    suspend fun broadcast(category: SyncCategory, packet: Packet) {
        sessions.values.filter { it.prefs.allows(category) }.forEach { deliver(it, packet) }
    }

    suspend fun send(deviceId: String, category: SyncCategory, packet: Packet) {
        sessions[deviceId]?.takeIf { it.prefs.allows(category) }?.let { deliver(it, packet) }
    }

    /** Revokes the pairing and disconnects the device if it is online. */
    suspend fun remove(deviceId: String) {
        paired.update { list -> list.filterNot { it.id == deviceId } }
        settings.savePairedDevices(paired.value)
        sessions.remove(deviceId)?.let { s ->
            runCatching {
                s.socket.sendPacket(Packet.Kicked)
                s.socket.close()
            }
        }
        online.update { it - deviceId }
    }

    suspend fun removeAll() = paired.value.forEach { remove(it.id) }

    private suspend fun deliver(session: Session, packet: Packet) {
        runCatching { session.socket.sendPacket(packet) }
    }

    // ---- Incoming ----------------------------------------------------------------------------

    private suspend fun handle(socket: DefaultWebSocketServerSession) {
        val remote = socket.call.request.origin.remoteHost
        val hello = withTimeoutOrNull(HELLO_TIMEOUT_MS) { socket.receivePacket() } as? Packet.Hello
        if (hello == null) {
            socket.close()
            return
        }
        val device = when (val result = authenticate(hello, remote)) {
            is AuthResult.Accepted -> result.device
            is AuthResult.Denied -> {
                socket.sendPacket(Packet.Rejected(result.reason))
                socket.close()
                return
            }
        }

        val session = Session(device, socket)
        sessions.put(device.id, session)?.socket?.let { runCatching { it.close() } } // replace stale connection
        online.update { it + device.id }
        socket.sendPacket(Packet.Welcome(device.token, Build.MODEL))

        try {
            for (frame in socket.incoming) {
                when (val packet = frame.toPacket()) {
                    is Packet.Prefs -> {
                        session.prefs = packet.prefs
                        bus.publish(AppEvent.DevicePreferencesChanged(device.id))
                    }
                    is Packet.Ping -> socket.sendPacket(Packet.Pong(packet.sentAt))
                    else -> Unit
                }
            }
        } finally {
            if (sessions.remove(device.id, session)) online.update { it - device.id }
        }
    }

    private suspend fun authenticate(hello: Packet.Hello, remote: String): AuthResult = authLock.withLock {
        // 1. Returning device with a token
        if (hello.token != null) {
            val known = paired.value.firstOrNull { it.id == hello.deviceId }
            return@withLock if (known != null && safeEquals(known.token, hello.token)) {
                AuthResult.Accepted(known)
            } else {
                AuthResult.Denied(RejectReason.REVOKED)
            }
        }
        // 2. New device with the pairing code
        if (limiter.isLocked(remote)) return@withLock AuthResult.Denied(RejectReason.LOCKED)
        if (hello.code != null && safeEquals(_code.value, hello.code)) {
            limiter.reset(remote)
            val device = PairedDevice(hello.deviceId, hello.deviceName, newToken())
            paired.update { list -> list.filterNot { it.id == device.id } + device }
            settings.savePairedDevices(paired.value)
            AuthResult.Accepted(device)
        } else {
            limiter.recordFailure(remote)
            AuthResult.Denied(RejectReason.INVALID_CODE)
        }
    }

    private fun safeEquals(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun newToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /** Locks an address out for a minute after 5 wrong codes. */
    private class AttemptLimiter(private val maxFailures: Int = 5, private val lockMs: Long = 60_000) {
        private val failures = ConcurrentHashMap<String, Int>()
        private val lockedUntil = ConcurrentHashMap<String, Long>()

        fun isLocked(key: String) = (lockedUntil[key] ?: 0L) > System.currentTimeMillis()

        fun recordFailure(key: String) {
            val count = (failures[key] ?: 0) + 1
            if (count >= maxFailures) {
                lockedUntil[key] = System.currentTimeMillis() + lockMs
                failures.remove(key)
            } else {
                failures[key] = count
            }
        }

        fun reset(key: String) {
            failures.remove(key)
        }
    }

    private companion object {
        const val HELLO_TIMEOUT_MS = 10_000L
    }
}
