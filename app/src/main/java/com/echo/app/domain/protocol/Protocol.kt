package com.echo.app.domain.protocol

import com.echo.app.domain.model.ClipItem
import com.echo.app.domain.model.RejectReason
import com.echo.app.domain.model.SmsMessage
import com.echo.app.domain.model.SyncPrefs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Protocol {
    const val PORT = 8765
    const val PATH = "/sync"
    const val SERVICE_TYPE = "_echosync._tcp."
    const val CODE_LENGTH = 6
    const val HEARTBEAT_MS = 5_000L
}

/** Everything that travels over the WebSocket, as JSON text frames. */
@Serializable
sealed class Packet {
    // Handshake
    @Serializable @SerialName("hello")
    data class Hello(
        val deviceId: String,
        val deviceName: String,
        val code: String? = null,
        val token: String? = null,
    ) : Packet()

    @Serializable @SerialName("welcome")
    data class Welcome(val token: String, val hostName: String) : Packet()

    @Serializable @SerialName("rejected")
    data class Rejected(val reason: RejectReason) : Packet()

    // Client -> host
    @Serializable @SerialName("prefs")
    data class Prefs(val prefs: SyncPrefs) : Packet()

    @Serializable @SerialName("ping")
    data class Ping(val sentAt: Long) : Packet()

    // Host -> client
    @Serializable @SerialName("pong")
    data class Pong(val sentAt: Long) : Packet()

    @Serializable @SerialName("messages")
    data class Messages(val items: List<SmsMessage>) : Packet()

    /** [live] is false for the history snapshot sent after connecting. */
    @Serializable @SerialName("clips")
    data class Clips(val items: List<ClipItem>, val live: Boolean = true) : Packet()

    @Serializable @SerialName("kicked")
    data object Kicked : Packet()
}

object PacketCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(packet: Packet): String = json.encodeToString(Packet.serializer(), packet)
    fun decode(text: String): Packet = json.decodeFromString(Packet.serializer(), text)
}
