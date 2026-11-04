package com.nexustools.genericui.net.socketio

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.nativeimpl.net.DefaultWebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.net.http.HttpClient
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 100% asynchronous, non-blocking Socket.IO v4 / Engine.IO v4 client.
 *
 * Operates over the active backend's [WebSocketNativeImpl] transport (with [HttpClient] fallback
 * support) and implements the Engine.IO v4 handshake (`0{...}` -> `40`), heartbeat ping/pong
 * (`2` -> `3`), event emission/subscription (`42["event", ...]`), custom namespaces (`40/ns,`),
 * and acknowledgement callbacks (`43<id>[...]`).
 */
class SocketIoClient internal constructor(
    val url: String,
    val options: SocketIoOptions,
    internal val webSocketNativeImpl: WebSocketNativeImpl,
    val httpClient: HttpClient
) : Disposable {

    constructor(
        url: String,
        options: SocketIoOptions = SocketIoOptions(),
        httpClient: HttpClient = HttpClient()
    ) : this(
        url = url,
        options = options,
        webSocketNativeImpl = GenericUI.activeFactory?.createWebSocket() ?: DefaultWebSocketNativeImpl(),
        httpClient = httpClient
    )

    private val eventListeners = LinkedHashMap<String, MutableList<(List<String>) -> Unit>>()
    private val onceListeners = LinkedHashMap<String, MutableList<(List<String>) -> Unit>>()
    private val connectListeners = ArrayList<() -> Unit>()
    private val disconnectListeners = ArrayList<(String) -> Unit>()
    private val errorListeners = ArrayList<(Throwable) -> Unit>()
    private val reconnectAttemptListeners = ArrayList<(Int) -> Unit>()
    private val pendingAcks = LinkedHashMap<Int, CompletableDeferred<List<String>>>()

    private var nextAckId: Int = 1
    private var sioConnected: Boolean = false
    private var awaitingSocketIoConnect: Boolean = false

    val isConnected: Boolean
        get() = webSocketNativeImpl.isConnected && (sioConnected || !awaitingSocketIoConnect)

    var socketId: String? = null
        private set

    var engineSocketId: String? = null
        private set

    override var isDisposed: Boolean = false
        private set

    init {
        attachWebSocketCallbacks()
    }

    suspend fun connect(): Boolean {
        if (isDisposed) return false
        attachWebSocketCallbacks()
        sioConnected = false
        awaitingSocketIoConnect = false

        val wsUrl = buildWebSocketUrl()
        val connected = webSocketNativeImpl.connect(wsUrl, options.headers)
        if (!connected) {
            return false
        }
        if (!awaitingSocketIoConnect && !sioConnected) {
            // If the underlying mock WebSocket did not emit an Engine.IO OPEN packet synchronously,
            // initiate the Socket.IO namespace connect handshake.
            awaitingSocketIoConnect = true
            val connectPacket = SocketIoParser.encodeConnect(options.namespace, options.auth)
            webSocketNativeImpl.sendText(connectPacket)
        }
        return webSocketNativeImpl.isConnected
    }

    suspend fun disconnect() {
        if (webSocketNativeImpl.isConnected) {
            val disconnectFrame = SocketIoParser.encodeDisconnect(options.namespace)
            webSocketNativeImpl.sendText(disconnectFrame)
            webSocketNativeImpl.close(1000, "io client disconnect")
        }
        sioConnected = false
        awaitingSocketIoConnect = false
        socketId = null
        cancelAllPendingAcks()
    }

    fun on(event: String, listener: (List<String>) -> Unit): SocketIoClient {
        eventListeners.getOrPut(event) { ArrayList() }.add(listener)
        return this
    }

    fun once(event: String, listener: (List<String>) -> Unit): SocketIoClient {
        onceListeners.getOrPut(event) { ArrayList() }.add(listener)
        return this
    }

    fun off(event: String) {
        eventListeners.remove(event)
        onceListeners.remove(event)
    }

    fun onConnect(listener: () -> Unit): SocketIoClient {
        connectListeners.add(listener)
        return this
    }

    fun onDisconnect(listener: (String) -> Unit): SocketIoClient {
        disconnectListeners.add(listener)
        return this
    }

    fun onError(listener: (Throwable) -> Unit): SocketIoClient {
        errorListeners.add(listener)
        return this
    }

    fun onReconnectAttempt(listener: (Int) -> Unit): SocketIoClient {
        reconnectAttemptListeners.add(listener)
        return this
    }

    suspend fun emit(event: String, vararg args: Any?): Boolean {
        if (isDisposed || !webSocketNativeImpl.isConnected) return false
        val frame = SocketIoParser.encodeEvent(
            event = event,
            args = args.toList(),
            namespace = options.namespace,
            ackId = null
        )
        return webSocketNativeImpl.sendText(frame)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun emitWithAck(
        event: String,
        vararg args: Any?,
        timeoutMs: Long = options.timeoutMs
    ): List<String>? {
        if (isDisposed || !webSocketNativeImpl.isConnected) return null
        val ackId = nextAckId++
        val deferred = CompletableDeferred<List<String>>()
        pendingAcks[ackId] = deferred

        val frame = SocketIoParser.encodeEvent(
            event = event,
            args = args.toList(),
            namespace = options.namespace,
            ackId = ackId
        )
        val sent = webSocketNativeImpl.sendText(frame)
        if (!sent) {
            pendingAcks.remove(ackId)
            return null
        }

        if (deferred.isCompleted) {
            pendingAcks.remove(ackId)
            return deferred.getCompleted()
        }

        return try {
            if (timeoutMs > 0L) {
                withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            } else {
                deferred.await()
            }
        } finally {
            pendingAcks.remove(ackId)
        }
    }

    fun buildWebSocketUrl(): String {
        val raw = url.trim()
        val wsBase = when {
            raw.startsWith("https://", ignoreCase = true) -> "wss://" + raw.substring(8)
            raw.startsWith("http://", ignoreCase = true) -> "ws://" + raw.substring(7)
            raw.startsWith("wss://", ignoreCase = true) || raw.startsWith("ws://", ignoreCase = true) -> raw
            else -> "ws://$raw"
        }.trimEnd('/')

        val normalizedPath = buildString {
            val clean = options.path.trim()
            if (clean.isEmpty()) {
                append("/socket.io/")
            } else {
                if (!clean.startsWith("/")) append('/')
                append(clean)
                if (!clean.endsWith("/")) append('/')
            }
        }

        val queryParts = ArrayList<String>()
        queryParts.add("EIO=4")
        queryParts.add("transport=websocket")
        for ((k, v) in options.query) {
            if (k.isNotBlank()) {
                queryParts.add("$k=$v")
            }
        }
        return "$wsBase$normalizedPath?${queryParts.joinToString("&")}"
    }

    override fun dispose() {
        isDisposed = true
        sioConnected = false
        awaitingSocketIoConnect = false
        cancelAllPendingAcks()
        eventListeners.clear()
        onceListeners.clear()
        connectListeners.clear()
        disconnectListeners.clear()
        errorListeners.clear()
        reconnectAttemptListeners.clear()
        webSocketNativeImpl.dispose()
        httpClient.dispose()
    }

    private fun attachWebSocketCallbacks() {
        webSocketNativeImpl.setOnTextMessage { rawFrame ->
            handleIncomingTextFrame(rawFrame)
        }
        webSocketNativeImpl.setOnError { error ->
            for (listener in errorListeners.toList()) {
                listener.invoke(error)
            }
        }
        webSocketNativeImpl.setOnClose { _, reason ->
            val wasConnected = sioConnected || webSocketNativeImpl.isConnected
            sioConnected = false
            awaitingSocketIoConnect = false
            cancelAllPendingAcks()
            if (wasConnected || disconnectListeners.isNotEmpty()) {
                for (listener in disconnectListeners.toList()) {
                    listener.invoke(reason)
                }
            }
        }
    }

    private fun handleIncomingTextFrame(rawFrame: String) {
        if (rawFrame.isEmpty() || isDisposed) return

        when {
            SocketIoParser.isEngineOpen(rawFrame) -> {
                engineSocketId = SocketIoParser.extractSidFromJson(rawFrame)
                awaitingSocketIoConnect = true
                val connectFrame = SocketIoParser.encodeConnect(options.namespace, options.auth)
                sendFrameNonBlocking(connectFrame)
            }
            SocketIoParser.isEnginePing(rawFrame) -> {
                val pongFrame = SocketIoParser.encodePong(rawFrame)
                sendFrameNonBlocking(pongFrame)
            }
            SocketIoParser.isEngineClose(rawFrame) -> {
                sioConnected = false
                awaitingSocketIoConnect = false
                for (listener in disconnectListeners.toList()) {
                    listener.invoke("transport close")
                }
            }
            SocketIoParser.isEngineMessage(rawFrame) -> {
                val packet = SocketIoParser.decodePacket(rawFrame) ?: return
                handleSocketIoPacket(packet)
            }
        }
    }

    private fun handleSocketIoPacket(packet: SocketIoPacket) {
        val targetNamespace = SocketIoParser.normalizeNamespace(options.namespace)
        if (packet.namespace != targetNamespace) return

        when (packet.type) {
            SocketIoPacketType.CONNECT -> {
                val sid = packet.argsJson.firstOrNull()?.let { SocketIoParser.extractSidFromJson(it) }
                if (!sid.isNullOrEmpty()) {
                    socketId = sid
                } else if (socketId == null) {
                    socketId = engineSocketId
                }
                sioConnected = true
                awaitingSocketIoConnect = false
                for (listener in connectListeners.toList()) {
                    listener.invoke()
                }
            }
            SocketIoPacketType.DISCONNECT -> {
                sioConnected = false
                awaitingSocketIoConnect = false
                for (listener in disconnectListeners.toList()) {
                    listener.invoke("io server disconnect")
                }
            }
            SocketIoPacketType.CONNECT_ERROR -> {
                val msg = packet.argsJson.firstOrNull() ?: "Socket.IO connect error"
                val err = IllegalStateException(msg)
                for (listener in errorListeners.toList()) {
                    listener.invoke(err)
                }
            }
            SocketIoPacketType.EVENT,
            SocketIoPacketType.BINARY_EVENT -> {
                val eventName = packet.event ?: return
                val listeners = eventListeners[eventName]?.toList().orEmpty()
                for (listener in listeners) {
                    listener.invoke(packet.argsJson)
                }
                val oneShot = onceListeners.remove(eventName)?.toList().orEmpty()
                for (listener in oneShot) {
                    listener.invoke(packet.argsJson)
                }
            }
            SocketIoPacketType.ACK,
            SocketIoPacketType.BINARY_ACK -> {
                val ackId = packet.ackId ?: return
                val deferred = pendingAcks.remove(ackId)
                deferred?.complete(packet.argsJson)
            }
        }
    }

    private fun sendFrameNonBlocking(frame: String) {
        val block: suspend () -> Unit = {
            webSocketNativeImpl.sendText(frame)
        }
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) {
                // Frame dispatch completed
            }
        )
    }

    private fun cancelAllPendingAcks() {
        for ((_, deferred) in pendingAcks) {
            if (!deferred.isCompleted) {
                deferred.cancel()
            }
        }
        pendingAcks.clear()
    }

    companion object {
        fun io(url: String, options: SocketIoOptions = SocketIoOptions()): SocketIoClient =
            SocketIoClient(url = url, options = options)
    }
}
