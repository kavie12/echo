package com.echo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.Role
import com.echo.app.service.SyncService
import com.echo.app.ui.client.ClientScreen
import com.echo.app.ui.host.HostScreen
import com.echo.app.ui.onboarding.OnboardingScreen

/** Chooses the screen from the persisted role and keeps the sync service running for it. */
@Composable
fun EchoRoot(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
    val startup by vm.startup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val role = (startup as? Startup.Ready)?.role

    LaunchedEffect(role) { if (role != null) SyncService.start(context, role) }

    when (val s = startup) {
        Startup.Loading -> Box(Modifier.fillMaxSize())
        is Startup.Ready -> when (s.role) {
            null -> OnboardingScreen(onSelect = vm::selectRole)
            Role.HOST -> HostScreen(container, onReset = vm::reset)
            Role.CLIENT -> ClientScreen(container, onReset = vm::reset)
        }
    }
}
