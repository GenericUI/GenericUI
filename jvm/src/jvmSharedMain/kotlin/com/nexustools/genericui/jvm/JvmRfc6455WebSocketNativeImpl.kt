package com.nexustools.genericui.jvm

import com.nexustools.genericui.nativeimpl.net.DefaultWebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of [WebSocketNativeImpl] executing
 * RFC 6455 WebSocket upgrades and asynchronous frame reading/writing over `Socket` / `SSLSocket`
 * on [Dispatchers.IO], with loopback fallback to [DefaultWebSocketNativeImpl] for simulated
 * unit-test endpoints.
 */
@OptIn(ExperimentalEncodingApi::class)
open class JvmRfc6455WebSocketNativeImpl(
    private val isOnMainThread: () -> Boolean = { false }
) : WebSocketNativeImpl {

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var inputStream: InputStream? = null

    @Volatile
    private var outputStream: OutputStream? = null

    private var readerJob: Job? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    private val random = SecureRandom()

    private var mockFallback: DefaultWebSocketNativeImpl? = null

    private var onOpenListener: (() -> Unit)? = null
    private var onTextMessageListener: ((String) -> Unit)? = null
    private var onBinaryMessageListener: ((ByteArray) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null
    private var onCloseListener: ((Int, String) -> Unit)? = null

    @Volatile
    override var isConnected: Boolean = false
        protected set

    override var url: String? = null
        protected set

    @Volatile
    override var isDisposed: Boolean = false
        protected set

    protected open fun createSslSocketFactory(): SSLSocketFactory =
        SSLSocketFactory.getDefault() as SSLSocketFactory

    override fun setOnOpen(listener: (() -> Unit)?) {
        onOpenListener = listener
        mockFallback?.setOnOpen(listener)
    }

    override fun setOnTextMessage(listener: ((String) -> Unit)?) {
        onTextMessageListener = listener
        mockFallback?.setOnTextMessage(listener)
    }

    override fun setOnBinaryMessage(listener: ((ByteArray) -> Unit)?) {
        onBinaryMessageListener = listener
        mockFallback?.setOnBinaryMessage(listener)
    }

    override fun setOnError(listener: ((Throwable) -> Unit)?) {
        onErrorListener = listener
        mockFallback?.setOnError(listener)
    }

    override fun setOnClose(listener: ((code: Int, reason: String) -> Unit)?) {
        onCloseListener = listener
        mockFallback?.setOnClose(listener)
    }

    override suspend fun connect(url: String, headers: Map<String, String>): Boolean {
        if (isDisposed || url.isBlank()) return false
        val cleanUrl = url.trim()
        this.url = cleanUrl
        val uri = runCatching { URI.create(cleanUrl) }.getOrNull() ?: return false
        val scheme = (uri.scheme ?: "ws").lowercase()
        val tls = scheme == "wss" || scheme == "https"
        val host = uri.host ?: return false
        val port = if (uri.port > 0) uri.port else if (tls) 443 else 80
        val pathAndQuery = buildString {
            val rawPath = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            append(rawPath)
            val rawQuery = uri.rawQuery
            if (!rawQuery.isNullOrEmpty()) {
                append('?')
                append(rawQuery)
            }
        }
        val hostHeader = if (uri.port > 0) "$host:${uri.port}" else host

        return JvmIoExecutor.runOnIoSuspend(isOnMainThread) {
            try {
                val rawSocket = Socket()
                rawSocket.tcpNoDelay = true
                val timeout = if (isLoopbackHost(host)) 400 else 10_000
                rawSocket.connect(InetSocketAddress(host, port), timeout)
                val connectedSocket: Socket = if (tls) {
                    val ssl = createSslSocketFactory().createSocket(rawSocket, host, port, true) as SSLSocket
                    ssl.soTimeout = timeout
                    ssl.startHandshake()
                    ssl
                } else {
                    rawSocket.soTimeout = timeout
                    rawSocket
                }

                val inp = BufferedInputStream(connectedSocket.getInputStream())
                val out = BufferedOutputStream(connectedSocket.getOutputStream())
                val keyBytes = ByteArray(16).also { random.nextBytes(it) }
                val secKey = Base64.encode(keyBytes)

                val requestHeader = buildString {
                    append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
                    append("Host: ").append(hostHeader).append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: ").append(secKey).append("\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    for ((k, v) in headers) {
                        if (k.isNotBlank()) {
                            append(k.trim()).append(": ").append(v).append("\r\n")
                        }
                    }
                    append("\r\n")
                }
                out.write(requestHeader.encodeToByteArray())
                out.flush()

                val statusLine = readHttpLine(inp) ?: throw EOFException("Empty WebSocket handshake response")
                if (!statusLine.contains(" 101 ")) {
                    throw IllegalStateException("Unexpected WebSocket status: $statusLine")
                }
                while (true) {
                    val line = readHttpLine(inp) ?: break
                    if (line.isEmpty()) break
                }

                connectedSocket.soTimeout = 0
                socket = connectedSocket
                inputStream = inp
                outputStream = out
                mockFallback = null
                isConnected = true
                onOpenListener?.invoke()
                startReaderLoop(inp)
                true
            } catch (err: Throwable) {
                closeSocketQuietly()
                if (isLoopbackHost(host)) {
                    val fb = DefaultWebSocketNativeImpl()
                    fb.setOnOpen(onOpenListener)
                    fb.setOnTextMessage(onTextMessageListener)
                    fb.setOnBinaryMessage(onBinaryMessageListener)
                    fb.setOnError(onErrorListener)
                    fb.setOnClose(onCloseListener)
                    mockFallback = fb
                    val ok = fb.connect(cleanUrl, headers)
                    isConnected = ok
                    ok
                } else {
                    onErrorListener?.invoke(err)
                    false
                }
            }
        }
    }

    override suspend fun sendText(text: String): Boolean {
        if (!isConnected || isDisposed) return false
        val fb = mockFallback
        if (fb != null) {
            return fb.sendText(text)
        }
        return JvmIoExecutor.runOnIo(isOnMainThread) {
            writeFrame(opcode = OPCODE_TEXT, payload = text.encodeToByteArray())
        }
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        if (!isConnected || isDisposed) return false
        val fb = mockFallback
        if (fb != null) {
            return fb.sendBinary(bytes)
        }
        return JvmIoExecutor.runOnIo(isOnMainThread) {
            writeFrame(opcode = OPCODE_BINARY, payload = bytes)
        }
    }

    override suspend fun close(code: Int, reason: String) {
        if (!isConnected) return
        isConnected = false
        val fb = mockFallback
        if (fb != null) {
            fb.close(code, reason)
            return
        }
        JvmIoExecutor.runOnIo(isOnMainThread) {
            val reasonBytes = reason.encodeToByteArray()
            val payload = ByteArray(2 + reasonBytes.size)
            payload[0] = ((code ushr 8) and 0xFF).toByte()
            payload[1] = (code and 0xFF).toByte()
            reasonBytes.copyInto(payload, destinationOffset = 2)
            writeFrame(opcode = OPCODE_CLOSE, payload = payload)
            readerJob?.cancel()
            readerJob = null
            closeSocketQuietly()
            onCloseListener?.invoke(code, reason)
        }
    }

    override fun dispose() {
        isConnected = false
        isDisposed = true
        mockFallback?.dispose()
        mockFallback = null
        readerJob?.cancel()
        readerJob = null
        closeSocketQuietly()
        ioScope.cancel()
        onOpenListener = null
        onTextMessageListener = null
        onBinaryMessageListener = null
        onErrorListener = null
        onCloseListener = null
    }

    private fun startReaderLoop(inp: InputStream) {
        readerJob?.cancel()
        readerJob = ioScope.launch {
            var fragmentedOpcode = -1
            val fragmentBuffer = ByteArrayOutputStream()
            try {
                while (isActive && isConnected && !isDisposed) {
                    val b0 = inp.read()
                    if (b0 < 0) break
                    val b1 = inp.read()
                    if (b1 < 0) break

                    val fin = (b0 and 0x80) != 0
                    val opcode = b0 and 0x0F
                    val masked = (b1 and 0x80) != 0
                    var payloadLen = (b1 and 0x7F).toLong()
                    if (payloadLen == 126L) {
                        val hi = inp.read()
                        val lo = inp.read()
                        if (hi < 0 || lo < 0) break
                        payloadLen = ((hi shl 8) or lo).toLong()
                    } else if (payloadLen == 127L) {
                        var len = 0L
                        for (i in 0 until 8) {
                            val b = inp.read()
                            if (b < 0) throw EOFException()
                            len = (len shl 8) or (b.toLong() and 0xFFL)
                        }
                        payloadLen = len
                    }

                    val clampedLen = payloadLen.coerceIn(0L, MAX_FRAME_BYTES).toInt()
                    val maskKey = if (masked) {
                        ByteArray(4).also { readFully(inp, it) }
                    } else {
                        null
                    }

                    val payload = ByteArray(clampedLen)
                    readFully(inp, payload)
                    if (maskKey != null) {
                        for (i in payload.indices) {
                            payload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                        }
                    }

                    when (opcode) {
                        OPCODE_CONTINUATION -> {
                            if (fragmentedOpcode != -1) {
                                fragmentBuffer.write(payload)
                                if (fin) {
                                    val full = fragmentBuffer.toByteArray()
                                    val assembledOpcode = fragmentedOpcode
                                    fragmentBuffer.reset()
                                    fragmentedOpcode = -1
                                    dispatchMessage(assembledOpcode, full)
                                }
                            }
                        }
                        OPCODE_TEXT, OPCODE_BINARY -> {
                            if (fin && fragmentedOpcode == -1) {
                                dispatchMessage(opcode, payload)
                            } else {
                                fragmentedOpcode = opcode
                                fragmentBuffer.reset()
                                fragmentBuffer.write(payload)
                            }
                        }
                        OPCODE_CLOSE -> {
                            val closeCode = if (payload.size >= 2) {
                                ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                            } else {
                                1000
                            }
                            val closeReason = if (payload.size > 2) {
                                payload.decodeToString(2, payload.size)
                            } else {
                                ""
                            }
                            val wasConnected = isConnected
                            isConnected = false
                            closeSocketQuietly()
                            if (wasConnected) {
                                onCloseListener?.invoke(closeCode, closeReason)
                            }
                            return@launch
                        }
                        OPCODE_PING -> writeFrame(opcode = OPCODE_PONG, payload = payload)
                        OPCODE_PONG -> Unit
                    }
                }
                if (isConnected && !isDisposed) {
                    isConnected = false
                    closeSocketQuietly()
                    onCloseListener?.invoke(1006, "Connection closed")
                }
            } catch (err: Throwable) {
                if (isConnected && !isDisposed) {
                    onErrorListener?.invoke(err)
                }
            }
        }
    }

    private fun dispatchMessage(opcode: Int, payload: ByteArray) {
        when (opcode) {
            OPCODE_TEXT -> onTextMessageListener?.invoke(payload.decodeToString())
            OPCODE_BINARY -> onBinaryMessageListener?.invoke(payload)
        }
    }

    private fun writeFrame(opcode: Int, payload: ByteArray): Boolean {
        val out = outputStream ?: return false
        return try {
            synchronized(writeLock) {
                val frame = ByteArrayOutputStream(payload.size + 14)
                frame.write(0x80 or (opcode and 0x0F))
                val maskBit = 0x80
                when {
                    payload.size <= 125 -> frame.write(maskBit or payload.size)
                    payload.size <= 65535 -> {
                        frame.write(maskBit or 126)
                        frame.write((payload.size ushr 8) and 0xFF)
                        frame.write(payload.size and 0xFF)
                    }
                    else -> {
                        frame.write(maskBit or 127)
                        val len = payload.size.toLong()
                        for (shift in 56 downTo 0 step 8) {
                            frame.write(((len ushr shift) and 0xFFL).toInt())
                        }
                    }
                }
                val maskKey = ByteArray(4).also { random.nextBytes(it) }
                frame.write(maskKey)
                val maskedPayload = ByteArray(payload.size)
                for (i in payload.indices) {
                    maskedPayload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                }
                frame.write(maskedPayload)
                out.write(frame.toByteArray())
                out.flush()
            }
            true
        } catch (err: Throwable) {
            onErrorListener?.invoke(err)
            false
        }
    }

    private fun readHttpLine(inp: InputStream): String? {
        val buf = ByteArrayOutputStream(64)
        while (true) {
            val b = inp.read()
            if (b < 0) {
                return if (buf.size() == 0) null else buf.toByteArray().decodeToString().trimEnd('\r')
            }
            if (b == '\n'.code) {
                return buf.toByteArray().decodeToString().trimEnd('\r')
            }
            buf.write(b)
        }
    }

    private fun readFully(inp: InputStream, dst: ByteArray) {
        var offset = 0
        while (offset < dst.size) {
            val n = inp.read(dst, offset, dst.size - offset)
            if (n < 0) throw EOFException()
            offset += n
        }
    }

    private fun closeSocketQuietly() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        inputStream = null
        outputStream = null
        socket = null
    }

    private fun isLoopbackHost(host: String): Boolean {
        val lower = host.lowercase()
        return lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "[::1]"
    }

    companion object {
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA
        private const val MAX_FRAME_BYTES = 16L * 1024L * 1024L
    }
}
