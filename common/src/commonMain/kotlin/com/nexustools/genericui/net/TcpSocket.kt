package com.nexustools.genericui.net

import com.nexustools.genericui.io.Stream

/**
 * Node.js `net.Socket`-inspired duplex [Stream] (`Readable` + `Writable`) representing a TCP
 * (or TLS-over-TCP) socket connection.
 */
interface TcpSocket : Stream {
    val isConnected: Boolean
    val remoteHost: String?
    val remotePort: Int
    val localPort: Int

    suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Long = 10_000L,
        tls: Boolean = false
    ): Boolean

    suspend fun readText(maxBytes: Int = 65536): String? =
        read(maxBytes)?.toString("utf8")
}
