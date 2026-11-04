package com.nexustools.genericui.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Stream

/**
 * Node.js `dgram.Socket`-inspired duplex [Stream] (`Readable` + `Writable`) representing a UDP
 * unicast/broadcast/multicast datagram socket.
 */
interface UdpSocket : Stream {
    val isBound: Boolean
    val localAddress: String?
    val localPort: Int
    var remoteHost: String?
    var remotePort: Int
    var broadcastEnabled: Boolean

    fun supportsMulticast(): Boolean

    suspend fun bind(localHost: String = "0.0.0.0", localPort: Int = 0): Boolean

    suspend fun connect(remoteHost: String, remotePort: Int): Boolean

    suspend fun joinMulticastGroup(multicastAddress: String, interfaceName: String? = null): Boolean

    suspend fun leaveMulticastGroup(multicastAddress: String, interfaceName: String? = null): Boolean

    suspend fun send(
        bytes: ByteArray,
        host: String = remoteHost ?: "127.0.0.1",
        port: Int = remotePort
    ): Int

    suspend fun send(
        buffer: ByteBuffer,
        host: String = remoteHost ?: "127.0.0.1",
        port: Int = remotePort
    ): Int = send(buffer.remainingByteArray(), host, port)

    suspend fun sendText(
        text: String,
        host: String = remoteHost ?: "127.0.0.1",
        port: Int = remotePort
    ): Int = send(text.encodeToByteArray(), host, port)

    suspend fun receive(maxBytes: Int = 65536, timeoutMs: Long = 0L): UdpDatagram?
}
