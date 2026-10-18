package com.echo.app.domain.model

import com.echo.app.domain.protocol.Protocol
import kotlinx.serialization.Serializable

enum class Role { HOST, CLIENT }

enum class SyncCategory { MESSAGES, CLIPBOARD }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

enum class RejectReason { INVALID_CODE, REVOKED, LOCKED }

@Serializable
data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
)

@Serializable
data class ClipItem(
    val id: String,
    val text: String,
    val timestamp: Long,
)

/**
 * Synchronisation switches. On the host they control what is shared,
 * on a client they control what is received.
 */
@Serializable
data class SyncPrefs(
    val enabled: Boolean = true,
    val messages: Boolean = true,
    val clipboard: Boolean = true,
) {
    fun allows(category: SyncCategory): Boolean = enabled && when (category) {
        SyncCategory.MESSAGES -> messages
        SyncCategory.CLIPBOARD -> clipboard
    }

    fun with(category: SyncCategory, value: Boolean): SyncPrefs = when (category) {
        SyncCategory.MESSAGES -> copy(messages = value)
        SyncCategory.CLIPBOARD -> copy(clipboard = value)
    }
}

/** A client the host has paired with. [token] lets it reconnect without the code. */
@Serializable
data class PairedDevice(val id: String, val name: String, val token: String)

/** What the client remembers about its host. */
@Serializable
data class ClientPairing(val host: String, val port: Int, val token: String, val hostName: String)

data class DeviceInfo(val id: String, val name: String, val online: Boolean)

data class DiscoveredHost(val name: String, val host: String, val port: Int) {
    val address: String get() = if (port == Protocol.PORT) host else "$host:$port"
}
