package com.echo.app.engine

import com.echo.app.data.network.HostServer
import com.echo.app.data.repository.ClipboardRepository
import com.echo.app.data.repository.MessageRepository
import com.echo.app.data.settings.AppSettings

/** Returns the app to onboarding, telling connected clients first. */
class AppResetter(
    private val hostEngine: HostEngine,
    private val clientEngine: ClientEngine,
    private val hostServer: HostServer,
    private val settings: AppSettings,
    private val messages: MessageRepository,
    private val clips: ClipboardRepository,
) {
    suspend fun reset() {
        hostServer.removeAll()
        hostEngine.stop()
        clientEngine.stop()
        settings.clearAll()
        messages.clear()
        clips.clear()
    }
}
