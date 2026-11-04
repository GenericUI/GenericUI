package com.nexustools.genericui.net

import com.nexustools.genericui.io.ByteBuffer

/**
 * Represents a single UDP datagram packet received from or sent to a remote endpoint.
 */
data class UdpDatagram(
    val data: ByteArray,
    val remoteAddress: String,
    val remotePort: Int
) {
    val buffer: ByteBuffer
        get() = ByteBuffer.wrap(data)

    val text: String
        get() = data.decodeToString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpDatagram) return false
        return data.contentEquals(other.data) &&
            remoteAddress == other.remoteAddress &&
            remotePort == other.remotePort
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + remoteAddress.hashCode()
        result = 31 * result + remotePort
        return result
    }
}
