package com.nexustools.genericui.net

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.exception.RawSocketUnsupportedException

/**
 * Capability discovery and socket factory for cross-platform networking (`HTTP`, `WebSocket`, `TCP`, `UDP`).
 *
 * - **Desktop (`:kde`, `:gtk`, `:windows`, `:macos`, `:swing`)** and **Mobile (`:android`, `:ios`)**:
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
     *
     * @throws NativeUninitializedException if [GenericUI.activeFactory] has not been initialized.
     * @throws RawSocketUnsupportedException if the active backend does not support raw TCP sockets.
     */
    fun openTcpSocket(): TcpSocket {
        val factory = GenericUI.activeFactory
            ?: throw NativeUninitializedException("Network", "openTcpSocket")
        if (!factory.supportsTcp()) {
            throw RawSocketUnsupportedException("tcp", factory.backendTarget.displayName)
        }
        return factory.createTcpSocket()
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
     *
     * @throws NativeUninitializedException if [GenericUI.activeFactory] has not been initialized.
     * @throws RawSocketUnsupportedException if the active backend does not support raw UDP sockets.
     */
    fun openUdpSocket(): UdpSocket {
        val factory = GenericUI.activeFactory
            ?: throw NativeUninitializedException("Network", "openUdpSocket")
        if (!factory.supportsUdp()) {
            throw RawSocketUnsupportedException("udp", factory.backendTarget.displayName)
        }
        return factory.createUdpSocket()
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
