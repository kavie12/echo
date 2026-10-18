@file:OptIn(ExperimentalMaterial3Api::class)

package com.echo.app.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.ClipItem
import com.echo.app.domain.model.ConnectionState
import com.echo.app.domain.model.SmsMessage
import com.echo.app.domain.protocol.Protocol
import com.echo.app.ui.common.ConfirmDialog
import com.echo.app.ui.common.ConnectionStatus
import com.echo.app.ui.common.EmptyState
import com.echo.app.ui.common.ResetAppButton
import com.echo.app.ui.common.SectionTitle
import com.echo.app.ui.common.SyncSettingsCard
import com.echo.app.ui.common.relativeTime

@Composable
fun ClientScreen(container: AppContainer, onReset: () -> Unit) {
    val vm: ClientViewModel = viewModel(factory = viewModelFactory { initializer { ClientViewModel(container) } })
    val paired by vm.isPaired.collectAsStateWithLifecycle()
    when (paired) {
        null -> Box(Modifier.fillMaxSize())
        false -> ConnectScreen(vm, onReset)
        true -> ClientHome(vm, onReset)
    }
}

// ---- Connect ---------------------------------------------------------------------------------

@Composable
private fun ConnectScreen(vm: ClientViewModel, onReset: () -> Unit) {
    val discovered by vm.discovered.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val busy = state == ConnectionState.CONNECTING

    LaunchedEffect(discovered) {
        if (address.isBlank()) discovered.firstOrNull()?.let { address = it.address }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Echo · Client") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Enter the code shown on the host phone. Both phones must be on the same Wi-Fi or hotspot.")

            if (discovered.isNotEmpty()) {
                SectionTitle("Nearby hosts")
                discovered.forEach { host ->
                    AssistChip(onClick = { address = host.address }, label = { Text("${host.name} · ${host.host}") })
                }
            }

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("Host address") },
                placeholder = { Text("192.168.0.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(Protocol.CODE_LENGTH) },
                label = { Text("Pairing code") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = { vm.connect(address, code) },
                enabled = !busy && code.length == Protocol.CODE_LENGTH && address.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Connect")
            }
            ResetAppButton(onReset)
        }
    }
}

// ---- Home ------------------------------------------------------------------------------------

private enum class Tab(val label: String, val icon: ImageVector) {
    MESSAGES("Messages", Icons.Default.Email),
    CLIPBOARD("Clipboard", Icons.Default.ContentPaste),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
private fun ClientHome(vm: ClientViewModel, onReset: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.MESSAGES) }
    val state by vm.state.collectAsStateWithLifecycle()
    val latency by vm.latency.collectAsStateWithLifecycle()
    val hostName by vm.hostName.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val clips by vm.clips.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(hostName ?: "Host")
                    ConnectionStatus(state, latency)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.MESSAGES -> MessagesList(messages, receiving = prefs.enabled && prefs.messages)
                Tab.CLIPBOARD -> ClipboardList(clips, receiving = prefs.enabled && prefs.clipboard, onCopy = vm::copy)
                Tab.SETTINGS -> ClientSettings(vm, hostName, state, latency, onReset)
            }
        }
    }
}

@Composable
private fun MessagesList(items: List<SmsMessage>, receiving: Boolean) {
    if (items.isEmpty()) {
        EmptyState(
            if (receiving) "No messages yet" else "Receiving is off",
            if (receiving) "New messages from the host appear here." else "Turn on message receiving in Settings.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items, key = { it.id }) { m ->
            ListItem(
                overlineContent = { Text((if (m.incoming) "Received · " else "Sent · ") + relativeTime(m.timestamp)) },
                headlineContent = { Text(m.address) },
                supportingContent = { Text(m.body, maxLines = 4) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ClipboardList(items: List<ClipItem>, receiving: Boolean, onCopy: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyState(
            if (receiving) "Clipboard is empty" else "Receiving is off",
            if (receiving) "Text copied on the host appears here." else "Turn on clipboard receiving in Settings.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items, key = { it.id }) { clip ->
            ListItem(
                overlineContent = { Text(relativeTime(clip.timestamp)) },
                headlineContent = { Text(clip.text, maxLines = 5) },
                trailingContent = {
                    IconButton(onClick = { onCopy(clip.text) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ClientSettings(
    vm: ClientViewModel,
    hostName: String?,
    state: ConnectionState,
    latency: Long?,
    onReset: () -> Unit,
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var confirmDisconnect by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SyncSettingsCard(
                title = "Receiving",
                globalLabel = "Synchronization",
                messagesLabel = "Receive messages",
                clipboardLabel = "Receive clipboard",
                prefs = prefs,
                onGlobal = vm::setGlobal,
                onCategory = vm::setCategory,
                footnote = "Live clipboard text from the host is also placed on this phone’s clipboard.",
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Connection")
                    Text("Host: ${hostName ?: "—"}")
                    ConnectionStatus(state, latency)
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect from host")
            }
        }
        item { ResetAppButton(onReset) }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Disconnect from host?",
            text = "You will need the host’s code to connect again.",
            confirmLabel = "Disconnect",
            onConfirm = { confirmDisconnect = false; vm.disconnect() },
            onDismiss = { confirmDisconnect = false },
        )
    }
}
