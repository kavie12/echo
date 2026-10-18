package com.echo.app.engine

import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.data.clipboard.ClipboardAccess
import com.echo.app.data.network.ClientConnection
import com.echo.app.data.repository.ClipboardRepository
import com.echo.app.data.repository.MessageRepository
import com.echo.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Client use-cases: pair, reconnect, apply receiving switches, disconnect. */
class ClientEngine(
    private val scope: CoroutineScope,
    private val bus: EventBus,
    private val settings: AppSettings,
    private val connection: ClientConnection,
    private val clipboard: ClipboardAccess,
    private val messages: MessageRepository,
    private val clips: ClipboardRepository,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            launch { settings.syncPrefs.collect { connection.pushPrefs(it) } }
            // A live clip from the host lands on this phone's clipboard automatically.
            launch {
                bus.on<AppEvent.ClipsReceived>().filter { it.live }.collect { event ->
                    event.items.maxByOrNull { it.timestamp }?.let { clipboard.write(it.text) }
                }
            }
            settings.pairing.first()?.let {
                connection.start(ClientConnection.Target(it.host, it.port, token = it.token))
            }
        }
    }

    /** Stops running without forgetting the pairing (used when the service stops). */
    fun stop() {
        job?.cancel()
        job = null
        connection.stop()
    }

    fun pair(host: String, port: Int, code: String) =
        connection.start(ClientConnection.Target(host, port, code = code))

    /** User-initiated: leave the host and forget it. */
    fun disconnect() {
        scope.launch {
            connection.stop()
            settings.savePairing(null)
            messages.clear()
            clips.clear()
        }
    }
}
