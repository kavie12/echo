package com.echo.app.engine

import android.util.Log
import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.data.clipboard.ClipboardAccess
import com.echo.app.data.network.HostAdvertiser
import com.echo.app.data.network.HostServer
import com.echo.app.data.repository.ClipboardRepository
import com.echo.app.data.settings.AppSettings
import com.echo.app.data.sms.SmsSource
import com.echo.app.domain.model.SyncCategory
import com.echo.app.domain.protocol.Packet
import com.echo.app.domain.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Host use-cases. Listens to capture events and forwards them to clients,
 * honouring the host's own switches and each client's preferences.
 */
class HostEngine(
    private val scope: CoroutineScope,
    private val bus: EventBus,
    private val settings: AppSettings,
    private val server: HostServer,
    private val advertiser: HostAdvertiser,
    private val sms: SmsSource,
    private val clipboard: ClipboardAccess,
    private val clipboardHistory: ClipboardRepository,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            runCatching { server.start() }.onFailure { Log.e(TAG, "Could not start server", it) }
            advertiser.start(Protocol.PORT)
            sms.start()
            clipboard.start()

            launch {
                bus.on<AppEvent.MessageCaptured>().collect {
                    forward(SyncCategory.MESSAGES, Packet.Messages(listOf(it.message)))
                }
            }
            launch {
                bus.on<AppEvent.ClipboardCaptured>().collect {
                    forward(SyncCategory.CLIPBOARD, Packet.Clips(listOf(it.item)))
                }
            }
            launch { bus.on<AppEvent.DevicePreferencesChanged>().collect { sendSnapshot(it.deviceId) } }
            launch { resendWhenCategoryTurnedOn() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        sms.stop()
        clipboard.stop()
        advertiser.stop()
        server.stop()
    }

    /** Call after the SMS permission has been granted. */
    fun refreshSources() = sms.start()

    fun hasSmsPermission() = sms.hasPermission()

    private suspend fun forward(category: SyncCategory, packet: Packet) {
        if (settings.syncPrefs.first().allows(category)) server.broadcast(category, packet)
    }

    /** Sends recent history so a client that just connected, or just re-enabled a category, catches up. */
    private suspend fun sendSnapshot(deviceId: String, only: SyncCategory? = null) {
        val hostPrefs = settings.syncPrefs.first()
        for (category in SyncCategory.entries) {
            if (only != null && category != only) continue
            if (!hostPrefs.allows(category)) continue
            when (category) {
                SyncCategory.MESSAGES -> {
                    val items = withContext(Dispatchers.IO) { sms.recent() }
                    if (items.isNotEmpty()) server.send(deviceId, category, Packet.Messages(items))
                }
                SyncCategory.CLIPBOARD -> {
                    val items = clipboardHistory.items.value
                    if (items.isNotEmpty()) server.send(deviceId, category, Packet.Clips(items, live = false))
                }
            }
        }
    }

    private suspend fun resendWhenCategoryTurnedOn() {
        var previous = settings.syncPrefs.first()
        settings.syncPrefs.collect { current ->
            SyncCategory.entries
                .filter { current.allows(it) && !previous.allows(it) }
                .forEach { category -> server.connectedDeviceIds().forEach { sendSnapshot(it, category) } }
            previous = current
        }
    }

    private companion object { const val TAG = "HostEngine" }
}
