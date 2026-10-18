@file:OptIn(ExperimentalMaterial3Api::class)

package com.echo.app.ui.host

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echo.app.di.AppContainer
import com.echo.app.domain.model.DeviceInfo
import com.echo.app.domain.protocol.Protocol
import com.echo.app.ui.common.ConfirmDialog
import com.echo.app.ui.common.OnlineGreen
import com.echo.app.ui.common.ResetAppButton
import com.echo.app.ui.common.SectionTitle
import com.echo.app.ui.common.StatusDot
import com.echo.app.ui.common.SyncSettingsCard

@Composable
fun HostScreen(container: AppContainer, onReset: () -> Unit) {
    val vm: HostViewModel = viewModel(factory = viewModelFactory { initializer { HostViewModel(container) } })
    val code by vm.code.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val smsGranted by vm.smsGranted.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<DeviceInfo?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }
    LaunchedEffect(Unit) { permissionLauncher.launch(requiredPermissions()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Echo · Host") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PairingCard(code, addresses, vm::newCode) }
            if (!smsGranted) {
                item { PermissionCard { permissionLauncher.launch(requiredPermissions()) } }
            }
            item {
                SyncSettingsCard(
                    title = "Sharing",
                    globalLabel = "Synchronization",
                    messagesLabel = "Share messages",
                    clipboardLabel = "Share clipboard",
                    prefs = prefs,
                    onGlobal = vm::setGlobal,
                    onCategory = vm::setCategory,
                    footnote = "Android only lets Echo read the clipboard while it is open. " +
                        "To share in the background, tap “Send clipboard” in the Echo notification.",
                )
            }
            item { SectionTitle("Devices") }
            if (devices.isEmpty()) {
                item {
                    Text(
                        "No devices yet. Enter the code above on a client phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(devices, key = { it.id }) { device -> DeviceRow(device) { pendingRemoval = device } }
            item { ResetAppButton(onReset) }
        }
    }

    pendingRemoval?.let { device ->
        ConfirmDialog(
            title = "Remove ${device.name}?",
            text = "It will be disconnected and will need the code to pair again.",
            confirmLabel = "Remove",
            onConfirm = { vm.removeDevice(device.id); pendingRemoval = null },
            onDismiss = { pendingRemoval = null },
        )
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun PairingCard(code: String, addresses: List<String>, onNewCode: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Pairing code")
            Text(
                code.chunked(3).joinToString(" "),
                fontSize = 40.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Text(
                if (addresses.isEmpty()) "Connect to Wi-Fi or turn on your hotspot so clients can reach this phone."
                else "Address: " + addresses.joinToString(", ") { "$it:${Protocol.PORT}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNewCode) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New code")
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Messages need SMS access", style = MaterialTheme.typography.titleSmall)
            Text("Without it only the clipboard can be shared.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text("Grant access") }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceInfo, onRemove: () -> Unit) {
    ListItem(
        leadingContent = {
            StatusDot(if (device.online) OnlineGreen else MaterialTheme.colorScheme.outline)
        },
        headlineContent = { Text(device.name) },
        supportingContent = { Text(if (device.online) "Online" else "Offline") },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove ${device.name}") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
