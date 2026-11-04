package com.nexustools.genericui.net.socketio

/**
 * Socket.IO v4 wire protocol packet types.
 */
enum class SocketIoPacketType(val code: Int) {
    CONNECT(0),
    DISCONNECT(1),
    EVENT(2),
    ACK(3),
    CONNECT_ERROR(4),
    BINARY_EVENT(5),
    BINARY_ACK(6);

    companion object {
        fun fromCode(code: Int): SocketIoPacketType? =
            entries.firstOrNull { it.code == code }
    }
}
