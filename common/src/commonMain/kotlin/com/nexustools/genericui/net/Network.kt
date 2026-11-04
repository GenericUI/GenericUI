package com.nexustools.genericui.net

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl

/**
 * Capability discovery and socket factory for cross-platform networking (`HTTP`, `WebSocket`, `TCP`, `UDP`).
 *
 * - **Desktop (`:qt`, `:gtk`, `:windows`, `:macos`, `:swing`)** and **Mobile (`:android`, `:ios`)**:
 *   Supports HTTP/HTTPS, WebSocket (`ws://`/`wss://`), raw TCP sockets ([TcpSocket]), and UDP/multicast sockets ([UdpSocket]).
 * - **Web (`:web`)**:
 *   Supports HTTP/HTTPS (`fetch`) and WebSocket (`window.WebSocket`). Raw TCP and UDP sockets are not permitted by
 *   browser security sandboxes ([supportsTcp] = `false`, [supportsUdp] = `false`).
 */
object Network {

    fun supportsTcp(): Boolean =
        GenericUI.activeFactory?.supportsTcp() ?: true

    fun supportsUdp(): Boolean =
        GenericUI.activeFactory?.supportsUdp() ?: true

    fun supportsMulticastUdp(): Boolean =
        GenericUI.activeFactory?.supportsMulticastUdp() ?: supportsUdp()

    /**
     * Creates a new unconnected [TcpSocket] duplex [com.nexustools.genericui.io.Stream].
     */
    fun openTcpSocket(): TcpSocket {
        val factory = GenericUI.activeFactory
        return if (factory != null) {
            factory.createTcpSocket()
        } else {
            DefaultTcpSocketNativeImpl()
        }
    }

    /**
     * Creates a new [TcpSocket] duplex [com.nexustools.genericui.io.Stream] and connects it to [host]:[port].
     */
    suspend fun openTcpSocket(
        host: String,
        port: Int,
        timeoutMs: Long = 10_000L,
        tls: Boolean = false
    ): TcpSocket {
        val socket = openTcpSocket()
        socket.connect(host, port, timeoutMs, tls)
        return socket
    }

    /**
     * Creates a new unbound [UdpSocket] duplex [com.nexustools.genericui.io.Stream].
     */
    fun openUdpSocket(): UdpSocket {
        val factory = GenericUI.activeFactory
        return if (factory != null) {
            factory.createUdpSocket()
        } else {
            DefaultUdpSocketNativeImpl()
        }
    }

    /**
     * Creates a new [UdpSocket] duplex [com.nexustools.genericui.io.Stream] and binds it to [localHost]:[localPort].
     */
    suspend fun openUdpSocket(
        localHost: String = "0.0.0.0",
        localPort: Int = 0
    ): UdpSocket {
        val socket = openUdpSocket()
        socket.bind(localHost, localPort)
        return socket
    }
}
