package com.echo.app.data.network

import com.echo.app.domain.protocol.Packet
import com.echo.app.domain.protocol.PacketCodec
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText

suspend fun WebSocketSession.sendPacket(packet: Packet) = send(Frame.Text(PacketCodec.encode(packet)))

fun Frame.toPacket(): Packet? =
    (this as? Frame.Text)?.let { runCatching { PacketCodec.decode(it.readText()) }.getOrNull() }

/** Suspends until the next valid packet, or returns null if the socket closes. */
suspend fun WebSocketSession.receivePacket(): Packet? {
    while (true) {
        val frame = incoming.receiveCatching().getOrNull() ?: return null
        frame.toPacket()?.let { return it }
    }
}
