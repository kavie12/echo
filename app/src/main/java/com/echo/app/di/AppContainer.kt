package com.echo.app.di

import android.content.Context
import com.echo.app.core.EventBus
import com.echo.app.data.clipboard.ClipboardAccess
import com.echo.app.data.network.ClientConnection
import com.echo.app.data.network.HostAdvertiser
import com.echo.app.data.network.HostDiscovery
import com.echo.app.data.network.HostServer
import com.echo.app.data.repository.ClipboardRepository
import com.echo.app.data.repository.MessageRepository
import com.echo.app.data.settings.AppSettings
import com.echo.app.data.sms.SmsSource
import com.echo.app.domain.model.Role
import com.echo.app.engine.AppResetter
import com.echo.app.engine.ClientEngine
import com.echo.app.engine.HostEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-written dependency graph: small enough that a DI framework would only add noise. */
class AppContainer(val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bus = EventBus()
    val settings = AppSettings(appContext)

    val messageRepository = MessageRepository(bus, scope)
    val clipboardRepository = ClipboardRepository(bus, scope)

    val clipboardAccess = ClipboardAccess(appContext, bus)
    val hostServer = HostServer(bus, settings)
    val clientConnection = ClientConnection(settings, bus, scope)
    val discovery = HostDiscovery(appContext)

    val hostEngine = HostEngine(
        scope, bus, settings, hostServer,
        HostAdvertiser(appContext), SmsSource(appContext, bus, scope), clipboardAccess, clipboardRepository,
    )
    val clientEngine = ClientEngine(
        scope, bus, settings, clientConnection, clipboardAccess, messageRepository, clipboardRepository,
    )
    val resetter = AppResetter(
        hostEngine, clientEngine, hostServer, settings, messageRepository, clipboardRepository,
    )

    fun startEngine(role: Role) = when (role) {
        Role.HOST -> hostEngine.start()
        Role.CLIENT -> clientEngine.start()
    }

    fun stopEngines() {
        hostEngine.stop()
        clientEngine.stop()
    }
}
