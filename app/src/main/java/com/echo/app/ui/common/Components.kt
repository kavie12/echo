package com.echo.app.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.app.domain.model.ConnectionState
import com.echo.app.domain.model.SyncCategory
import com.echo.app.domain.model.SyncPrefs

val OnlineGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFFF9A825)

fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
fun ConnectionStatus(state: ConnectionState, latencyMs: Long?) {
    val (color, label) = when (state) {
        ConnectionState.CONNECTED -> OnlineGreen to "Connected" + (latencyMs?.let { " · $it ms" } ?: "")
        ConnectionState.CONNECTING -> WarningAmber to "Connecting…"
        ConnectionState.RECONNECTING -> WarningAmber to "Reconnecting…"
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error to "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(color)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Global switch plus one switch per category. Used by host ("share") and client ("receive"). */
@Composable
fun SyncSettingsCard(
    title: String,
    globalLabel: String,
    messagesLabel: String,
    clipboardLabel: String,
    prefs: SyncPrefs,
    onGlobal: (Boolean) -> Unit,
    onCategory: (SyncCategory, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionTitle(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SwitchRow(globalLabel, prefs.enabled, true, onGlobal)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SwitchRow(messagesLabel, prefs.messages, prefs.enabled) { onCategory(SyncCategory.MESSAGES, it) }
            SwitchRow(clipboardLabel, prefs.clipboard, prefs.enabled) { onCategory(SyncCategory.CLIPBOARD, it) }
            footnote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ResetAppButton(onReset: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by remember { mutableStateOf(false) }
    TextButton(
        onClick = { confirming = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier,
    ) { Text("Reset app") }
    if (confirming) {
        ConfirmDialog(
            title = "Reset Echo?",
            text = "This removes all pairing data and takes you back to setup.",
            confirmLabel = "Reset",
            onConfirm = { confirming = false; onReset() },
            onDismiss = { confirming = false },
        )
    }
}
