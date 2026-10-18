package com.echo.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.echo.app.domain.model.ClientPairing
import com.echo.app.domain.model.PairedDevice
import com.echo.app.domain.model.Role
import com.echo.app.domain.model.SyncPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "echo_settings")

private object Keys {
    val ROLE = stringPreferencesKey("role")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    val SYNC_MESSAGES = booleanPreferencesKey("sync_messages")
    val SYNC_CLIPBOARD = booleanPreferencesKey("sync_clipboard")
    val PAIRED_DEVICES = stringPreferencesKey("paired_devices")
    val PAIRING = stringPreferencesKey("pairing")
}

/** Single place that persists role, switches, paired devices and the client's pairing. */
class AppSettings(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val store get() = context.dataStore

    val role: Flow<Role?> = store.data.map { p ->
        p[Keys.ROLE]?.let { runCatching { Role.valueOf(it) }.getOrNull() }
    }

    suspend fun setRole(role: Role) {
        store.edit { it[Keys.ROLE] = role.name }
    }

    val syncPrefs: Flow<SyncPrefs> = store.data.map { it.toSyncPrefs() }.distinctUntilChanged()

    suspend fun updatePrefs(transform: (SyncPrefs) -> SyncPrefs) {
        store.edit { p ->
            val next = transform(p.toSyncPrefs())
            p[Keys.SYNC_ENABLED] = next.enabled
            p[Keys.SYNC_MESSAGES] = next.messages
            p[Keys.SYNC_CLIPBOARD] = next.clipboard
        }
    }

    val pairedDevices: Flow<List<PairedDevice>> = store.data.map { p ->
        p[Keys.PAIRED_DEVICES]?.let { runCatching { json.decodeFromString<List<PairedDevice>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun savePairedDevices(devices: List<PairedDevice>) {
        store.edit { it[Keys.PAIRED_DEVICES] = json.encodeToString(devices) }
    }

    val pairing: Flow<ClientPairing?> = store.data.map { p ->
        p[Keys.PAIRING]?.let { runCatching { json.decodeFromString<ClientPairing>(it) }.getOrNull() }
    }

    suspend fun savePairing(pairing: ClientPairing?) {
        store.edit { p ->
            if (pairing == null) p.remove(Keys.PAIRING) else p[Keys.PAIRING] = json.encodeToString(pairing)
        }
    }

    suspend fun deviceId(): String {
        var id = ""
        store.edit { p ->
            id = p[Keys.DEVICE_ID] ?: UUID.randomUUID().toString().also { p[Keys.DEVICE_ID] = it }
        }
        return id
    }

    /** Back to onboarding. The device id is kept; it is just a random identifier. */
    suspend fun clearAll() {
        store.edit { p ->
            val id = p[Keys.DEVICE_ID]
            p.clear()
            id?.let { p[Keys.DEVICE_ID] = it }
        }
    }

    private fun Preferences.toSyncPrefs() = SyncPrefs(
        enabled = this[Keys.SYNC_ENABLED] ?: true,
        messages = this[Keys.SYNC_MESSAGES] ?: true,
        clipboard = this[Keys.SYNC_CLIPBOARD] ?: true,
    )
}
