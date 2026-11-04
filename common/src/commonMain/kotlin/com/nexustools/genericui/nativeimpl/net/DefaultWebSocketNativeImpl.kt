package com.nexustools.genericui.nativeimpl.net

/**
 * In-memory mockable fallback implementation of [WebSocketNativeImpl].
 *
 * Supports simulated server text/binary frames, custom client message interceptors, and
 * automatic Engine.IO v4 / Socket.IO v4 handshake (`0{...}` -> `40{...}`) and ack replies
 * for headless unit testing.
 */
class DefaultWebSocketNativeImpl(
    var autoEngineIoHandshake: Boolean = true,
    var autoAckEvents: Boolean = true,
    var onClientTextHandler: ((DefaultWebSocketNativeImpl, String) -> Unit)? = null,
    var onClientBinaryHandler: ((DefaultWebSocketNativeImpl, ByteArray) -> Unit)? = null
) : WebSocketNativeImpl {

    private var onOpenListener: (() -> Unit)? = null
    private var onTextMessageListener: ((String) -> Unit)? = null
    private var onBinaryMessageListener: ((ByteArray) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null
    private var onCloseListener: ((Int, String) -> Unit)? = null

    private val sentTexts = ArrayList<String>()
    private val sentBinaries = ArrayList<ByteArray>()

    override var isConnected: Boolean = false
        private set

    override var url: String? = null
        private set

    var requestHeaders: Map<String, String> = emptyMap()
        private set

    override var isDisposed: Boolean = false
        private set

    override fun setOnOpen(listener: (() -> Unit)?) {
        onOpenListener = listener
    }

    override fun setOnTextMessage(listener: ((String) -> Unit)?) {
        onTextMessageListener = listener
    }

    override fun setOnBinaryMessage(listener: ((ByteArray) -> Unit)?) {
        onBinaryMessageListener = listener
    }

    override fun setOnError(listener: ((Throwable) -> Unit)?) {
        onErrorListener = listener
    }

    override fun setOnClose(listener: ((code: Int, reason: String) -> Unit)?) {
        onCloseListener = listener
    }

    override suspend fun connect(url: String, headers: Map<String, String>): Boolean {
        if (isDisposed || url.isBlank()) return false
        this.url = url.trim()
        this.requestHeaders = headers.toMap()
        this.isConnected = true

        onOpenListener?.invoke()

        if (autoEngineIoHandshake && (url.contains("EIO=4") || url.contains("socket.io"))) {
            simulateServerText(
                """0{"sid":"mock-eio-sid-1","upgrades":[],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}"""
            )
        }
        return true
    }

    override suspend fun sendText(text: String): Boolean {
        if (!isConnected || isDisposed) return false
        sentTexts.add(text)

        val customHandler = onClientTextHandler
        if (customHandler != null) {
            customHandler.invoke(this, text)
            return true
        }

        if (autoEngineIoHandshake) {
            handleAutomaticProtocolReply(text)
        }
        return true
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        if (!isConnected || isDisposed) return false
        val copy = bytes.copyOf()
        sentBinaries.add(copy)

        val customHandler = onClientBinaryHandler
        if (customHandler != null) {
            customHandler.invoke(this, copy)
        }
        return true
    }

    override suspend fun close(code: Int, reason: String) {
        if (!isConnected) return
        isConnected = false
        onCloseListener?.invoke(code, reason)
    }

    override fun dispose() {
        isConnected = false
        isDisposed = true
        onOpenListener = null
        onTextMessageListener = null
        onBinaryMessageListener = null
        onErrorListener = null
        onCloseListener = null
        sentTexts.clear()
        sentBinaries.clear()
    }

    fun simulateServerText(text: String) {
        if (!isDisposed) {
            onTextMessageListener?.invoke(text)
        }
    }

    fun simulateServerBinary(bytes: ByteArray) {
        if (!isDisposed) {
            onBinaryMessageListener?.invoke(bytes.copyOf())
        }
    }

    fun simulateServerError(error: Throwable) {
        if (!isDisposed) {
            onErrorListener?.invoke(error)
        }
    }

    fun simulateServerClose(code: Int = 1000, reason: String = "Server Closure") {
        if (!isDisposed) {
            isConnected = false
            onCloseListener?.invoke(code, reason)
        }
    }

    fun sentTextHistory(): List<String> = sentTexts.toList()

    fun sentBinaryHistory(): List<ByteArray> = sentBinaries.map { it.copyOf() }

    fun clearSentHistory() {
        sentTexts.clear()
        sentBinaries.clear()
    }

    private fun handleAutomaticProtocolReply(text: String) {
        when {
            text == "2" -> {
                simulateServerText("3")
            }
            text.startsWith("40") -> {
                val remainder = text.substring(2)
                val namespace = if (remainder.startsWith("/")) {
                    val commaIdx = remainder.indexOf(',')
                    if (commaIdx >= 0) remainder.substring(0, commaIdx) else remainder
                } else {
                    "/"
                }
                if (namespace == "/" || namespace.isEmpty()) {
                    simulateServerText("""40{"sid":"mock-sio-sid-1"}""")
                } else {
                    simulateServerText("""40$namespace,{"sid":"mock-sio-sid-1"}""")
                }
            }
            autoAckEvents && text.startsWith("42") -> {
                val remainder = text.substring(2)
                var cursor = 0
                var namespacePrefix = ""
                if (remainder.startsWith("/")) {
                    val commaIdx = remainder.indexOf(',')
                    if (commaIdx >= 0) {
                        namespacePrefix = remainder.substring(0, commaIdx + 1)
                        cursor = commaIdx + 1
                    }
                }
                val idStart = cursor
                while (cursor < remainder.length && remainder[cursor].isDigit()) {
                    cursor++
                }
                if (cursor > idStart) {
                    val ackIdStr = remainder.substring(idStart, cursor)
                    simulateServerText("""43$namespacePrefix$ackIdStr["ok"]""")
                }
            }
        }
    }
}
