package com.echo.app.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.app.data.network.LocalNetwork
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.DeviceInfo
import com.echo.app.domain.model.SyncCategory
import com.echo.app.domain.model.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostViewModel(private val container: AppContainer) : ViewModel() {
    val code: StateFlow<String> = container.hostServer.code

    val devices: StateFlow<List<DeviceInfo>> = container.hostServer.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prefs: StateFlow<SyncPrefs> = container.settings.syncPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncPrefs())

    val addresses: StateFlow<List<String>> = flow {
        while (true) {
            emit(LocalNetwork.ipv4Addresses())
            delay(5_000)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _smsGranted = MutableStateFlow(container.hostEngine.hasSmsPermission())
    val smsGranted: StateFlow<Boolean> = _smsGranted.asStateFlow()

    fun onPermissionsResult() {
        container.hostEngine.refreshSources()
        _smsGranted.value = container.hostEngine.hasSmsPermission()
    }

    fun setGlobal(enabled: Boolean) {
        viewModelScope.launch { container.settings.updatePrefs { it.copy(enabled = enabled) } }
    }

    fun setCategory(category: SyncCategory, enabled: Boolean) {
        viewModelScope.launch { container.settings.updatePrefs { it.with(category, enabled) } }
    }

    fun newCode() = container.hostServer.regenerateCode()

    fun removeDevice(id: String) {
        viewModelScope.launch { container.hostServer.remove(id) }
    }
}
