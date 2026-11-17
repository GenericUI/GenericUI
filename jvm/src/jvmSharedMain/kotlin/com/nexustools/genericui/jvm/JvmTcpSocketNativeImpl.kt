package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl] backed by `java.net.Socket`
 * and `javax.net.ssl.SSLSocket` on [kotlinx.coroutines.Dispatchers.IO].
 */
open class JvmTcpSocketNativeImpl(
    private val isOnMainThread: () -> Boolean = { false }
) : DefaultTcpSocketNativeImpl() {

    open val nativeApi: String = "java_net_Socket_SSLSocket_Dispatchers_IO"

    @Volatile
    private var activeSocket: Socket? = null
    private val readLock = Any()
    private val writeLock = Any()

    protected open fun createTrustManager(): TrustManager = JvmPermissiveX509TrustManager()

    override suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Long,
        tls: Boolean
    ): Boolean {
        if (isDisposed || host.isBlank() || port !in 1..65535) return false

        val isLoopbackHost = host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)
        if (isLoopbackHost && hasLoopbackServer(port)) {
            return super.connect(host, port, timeoutMs, tls)
        }

        closeNativeSocket()
        val clampedTimeout = timeoutMs.coerceIn(250L, 60_000L).toInt()

        val boundLocalPort = JvmIoExecutor.runOnIo(isOnMainThread) {
            runCatching {
                val rawSocket = Socket()
                rawSocket.tcpNoDelay = true
                rawSocket.keepAlive = true
                rawSocket.connect(InetSocketAddress(host.trim(), port), clampedTimeout)

                val finalSocket: Socket = if (tls) {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf(createTrustManager()), SecureRandom())
                    val sslSock = sslContext.socketFactory.createSocket(
                        rawSocket,
                        host.trim(),
                        port,
                        true
                    ) as SSLSocket
                    sslSock.soTimeout = clampedTimeout
                    sslSock.startHandshake()
                    sslSock
                } else {
                    rawSocket
                }
                finalSocket.soTimeout = 100
                activeSocket = finalSocket
                finalSocket.localPort
            }.getOrDefault(-1)
        }

        if (boundLocalPort < 0) return false
        markConnected(host, port, boundLocalPort)
        return true
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (isDisposed || isEnded) return null
        val sock = activeSocket
        if (sock != null && !sock.isClosed && !hasBufferedInbound()) {
            JvmIoExecutor.runOnIo(isOnMainThread) {
                pollSocketInbound(sock, waitMs = 250)
            }
        }
        return super.read(maxBytes)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        val sock = activeSocket
        if (sock == null) {
            return super.write(chunk, offset, length)
        }
        if (!isWritable || sock.isClosed) return false
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return true

        val slice = chunk.copyOfRange(offset, offset + length)
        val wroteOk = synchronized(writeLock) {
            runCatching {
                val out = sock.getOutputStream()
                out.write(slice)
                out.flush()
                true
            }.getOrDefault(false)
        }
        if (!wroteOk) return false
        return recordOutboundWrite(slice)
    }

    override fun dispose() {
        closeNativeSocket()
        super.dispose()
    }

    private fun closeNativeSocket() {
        val sock = activeSocket
        activeSocket = null
        if (sock != null) {
            runCatching { sock.close() }
        }
    }

    private fun pollSocketInbound(sock: Socket, waitMs: Int) {
        synchronized(readLock) {
            if (sock.isClosed || isDisposed) return
            val buf = ByteArray(16384)
            try {
                sock.soTimeout = waitMs.coerceAtLeast(1)
                val input = sock.getInputStream()
                val n = input.read(buf)
                if (n > 0) {
                    notifyInboundBytes(buf.copyOf(n))
                } else if (n < 0) {
                    markDisconnectedByPeer()
                }
            } catch (_: SocketTimeoutException) {
                // No bytes arrived within timeout window
            } catch (_: Throwable) {
                markDisconnectedByPeer()
            }
        }
    }

    companion object {
        fun clearLoopback() {
            clearLoopbackServers()
        }
    }
}
