package com.echo.app.data.network

import android.os.Build
import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.data.settings.AppSettings
import com.echo.app.domain.model.ClientPairing
import com.echo.app.domain.model.ConnectionState
import com.echo.app.domain.model.RejectReason
import com.echo.app.domain.model.SyncPrefs
import com.echo.app.domain.protocol.Packet
import com.echo.app.domain.protocol.Protocol
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Client side of the link. Pairs with a code, then reconnects automatically with the
 * saved token. Received data is published on the [EventBus].
 */
class ClientConnection(
    private val settings: AppSettings,
    private val bus: EventBus,
    private val scope: CoroutineScope,
) {
    data class Target(val host: String, val port: Int, val code: String? = null, val token: String? = null)

    private val http = HttpClient(CIO) {
        install(WebSockets) { pingInterval = 15_000 }
        engine { endpoint.connectTimeout = 5_000 }
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _latency = MutableStateFlow<Long?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var job: Job? = null
    @Volatile private var session: WebSocketSession? = null

    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val latencyMs: StateFlow<Long?> = _latency.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(target: Target) {
        job?.cancel()
        _error.value = null
        _state.value = ConnectionState.CONNECTING
        job = scope.launch { run(target) }
    }

    fun stop() {
        job?.cancel()
        job = null
        session = null
        _state.value = ConnectionState.DISCONNECTED
        _latency.value = null
    }

    /** Tells the host what this client wants to receive. */
    fun pushPrefs(prefs: SyncPrefs) {
        val current = session ?: return
        scope.launch { runCatching { current.sendPacket(Packet.Prefs(prefs)) } }
    }

    private suspend fun run(target: Target) {
        var token = target.token
        var code = target.code
        var attempt = 0
        var terminal = false

        while (currentCoroutineContext().isActive && !terminal) {
            try {
                http.webSocket(host = target.host, port = target.port, path = Protocol.PATH) {
                    session = this
                    sendPacket(Packet.Hello(settings.deviceId(), Build.MODEL, code, token))
                    when (val reply = receivePacket()) {
                        is Packet.Welcome -> {
                            token = reply.token
                            code = null
                            attempt = 0
                            settings.savePairing(ClientPairing(target.host, target.port, reply.token, reply.hostName))
                            _error.value = null
                            _state.value = ConnectionState.CONNECTED
                            sendPacket(Packet.Prefs(settings.syncPrefs.first()))
                            terminal = listen()
                        }
                        is Packet.Rejected -> {
                            terminal = true
                            onRejected(reply.reason)
                        }
                        else -> if (token == null) {
                            terminal = true
                            fail("Could not connect to the host")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == null) { // first pairing attempt: tell the user instead of retrying forever
                    terminal = true
                    fail("Can't reach the host. Check the address and Wi-Fi.")
                }
            } finally {
                session = null
            }
            if (!terminal) {
                _state.value = ConnectionState.RECONNECTING
                _latency.value = null
                delay(minOf(2_000L * (++attempt), 10_000L))
            }
        }
    }

    /** Reads packets until the socket closes. Returns true if the host removed this device. */
    private suspend fun DefaultClientWebSocketSession.listen(): Boolean {
        val heartbeat = launch {
            while (true) {
                sendPacket(Packet.Ping(System.currentTimeMillis()))
                delay(Protocol.HEARTBEAT_MS)
            }
        }
        try {
            for (frame in incoming) {
                when (val packet = frame.toPacket()) {
                    is Packet.Messages -> bus.publish(AppEvent.MessagesReceived(packet.items))
                    is Packet.Clips -> bus.publish(AppEvent.ClipsReceived(packet.items, packet.live))
                    is Packet.Pong -> _latency.value = System.currentTimeMillis() - packet.sentAt
                    is Packet.Kicked -> {
                        settings.savePairing(null)
                        fail("Removed by the host")
                        return true
                    }
                    else -> Unit
                }
            }
            return false
        } finally {
            heartbeat.cancel()
        }
    }

    private suspend fun onRejected(reason: RejectReason) {
        when (reason) {
            RejectReason.INVALID_CODE -> fail("Incorrect code")
            RejectReason.LOCKED -> fail("Too many attempts. Try again in a minute.")
            RejectReason.REVOKED -> {
                settings.savePairing(null)
                fail("This device was removed by the host")
            }
        }
    }

    private fun fail(message: String) {
        _error.value = message
        _state.value = ConnectionState.DISCONNECTED
    }
}
