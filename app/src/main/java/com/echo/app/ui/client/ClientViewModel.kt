package com.echo.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.ClientPairing
import com.echo.app.domain.model.ClipItem
import com.echo.app.domain.model.ConnectionState
import com.echo.app.domain.model.DiscoveredHost
import com.echo.app.domain.model.SmsMessage
import com.echo.app.domain.model.SyncCategory
import com.echo.app.domain.model.SyncPrefs
import com.echo.app.domain.protocol.Protocol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientViewModel(private val container: AppContainer) : ViewModel() {
    /** null while the saved pairing is still loading. */
    val isPaired: StateFlow<Boolean?> = container.settings.pairing
        .map<ClientPairing?, Boolean?> { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hostName: StateFlow<String?> = container.settings.pairing
        .map { it?.hostName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<ConnectionState> = container.clientConnection.state
    val latency: StateFlow<Long?> = container.clientConnection.latencyMs
    val error: StateFlow<String?> = container.clientConnection.error

    val prefs: StateFlow<SyncPrefs> = container.settings.syncPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncPrefs())

    val messages: StateFlow<List<SmsMessage>> = container.messageRepository.items
    val clips: StateFlow<List<ClipItem>> = container.clipboardRepository.items

    /** Discovery only runs while the connect screen is visible. */
    val discovered: StateFlow<List<DiscoveredHost>> = container.discovery.discover()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun connect(address: String, code: String) {
        val host = address.substringBefore(':').trim()
        val port = address.substringAfter(':', "").toIntOrNull() ?: Protocol.PORT
        container.clientEngine.pair(host, port, code)
    }

    fun disconnect() = container.clientEngine.disconnect()

    fun copy(text: String) = container.clipboardAccess.write(text)

    fun setGlobal(enabled: Boolean) {
        viewModelScope.launch { container.settings.updatePrefs { it.copy(enabled = enabled) } }
    }

    fun setCategory(category: SyncCategory, enabled: Boolean) {
        viewModelScope.launch { container.settings.updatePrefs { it.with(category, enabled) } }
    }
}
