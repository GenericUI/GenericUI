package com.nexustools.genericui.net.socketio

/**
 * Decoded or outgoing Socket.IO v4 packet.
 */
data class SocketIoPacket(
    val type: SocketIoPacketType,
    val namespace: String = "/",
    val ackId: Int? = null,
    val event: String? = null,
    val argsJson: List<String> = emptyList(),
    val binaryPayloads: List<ByteArray> = emptyList()
)
