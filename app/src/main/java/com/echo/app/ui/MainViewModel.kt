package com.echo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.Role
import com.echo.app.service.SyncService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Startup {
    data object Loading : Startup
    data class Ready(val role: Role?) : Startup
}

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val startup: StateFlow<Startup> = container.settings.role
        .map<Role?, Startup> { Startup.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Startup.Loading)

    fun selectRole(role: Role) {
        viewModelScope.launch { container.settings.setRole(role) }
    }

    fun reset() {
        SyncService.stop(container.appContext)
        viewModelScope.launch { container.resetter.reset() }
    }
}
