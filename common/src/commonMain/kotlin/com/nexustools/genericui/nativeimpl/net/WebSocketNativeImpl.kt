package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.core.Disposable

/**
 * Per-backend native contract for 100% asynchronous, non-blocking WebSocket connections
 * used by [com.nexustools.genericui.net.socketio.SocketIoClient] and direct WebSocket consumers.
 */
interface WebSocketNativeImpl : Disposable {
    val isConnected: Boolean
    val url: String?

    fun setOnOpen(listener: (() -> Unit)?)
    fun setOnTextMessage(listener: ((String) -> Unit)?)
    fun setOnBinaryMessage(listener: ((ByteArray) -> Unit)?)
    fun setOnError(listener: ((Throwable) -> Unit)?)
    fun setOnClose(listener: ((code: Int, reason: String) -> Unit)?)

    suspend fun connect(url: String, headers: Map<String, String> = emptyMap()): Boolean
    suspend fun sendText(text: String): Boolean
    suspend fun sendBinary(bytes: ByteArray): Boolean
    suspend fun close(code: Int = 1000, reason: String = "Normal Closure")
}
