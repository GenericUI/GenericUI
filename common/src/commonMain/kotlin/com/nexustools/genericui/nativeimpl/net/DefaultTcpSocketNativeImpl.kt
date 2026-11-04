package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.io.WritableByteBuffer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Default in-memory loopback implementation of [TcpSocketNativeImpl] implementing full
 * [com.nexustools.genericui.net.TcpSocket] and [Stream] (`Readable` + `Writable`) semantics.
 */
open class DefaultTcpSocketNativeImpl(
    private val supportedOverride: Boolean? = null
) : TcpSocketNativeImpl {

    override var isConnected: Boolean = false
        protected set

    override var remoteHost: String? = null
        protected set

    override var remotePort: Int = 0
        protected set

    override var localPort: Int = 0
        protected set

    override var byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    override var highWaterMark: Int = 65536

    private val inboundChunks = ArrayDeque<ByteBuffer>()
    private val outboundHistory = WritableByteBuffer(initialCapacity = 256, byteOrder = byteOrder)

    private var pausedState: Boolean = false
    private var endedState: Boolean = false
    private var finishedState: Boolean = false
    private var disposedState: Boolean = false
    private var totalRead: Long = 0L
    private var totalWritten: Long = 0L

    protected val dataListeners = mutableListOf<suspend (ByteBuffer) -> Unit>()
    protected val endListeners = mutableListOf<suspend () -> Unit>()
    protected val writeListeners = mutableListOf<(ByteBuffer) -> Unit>()
    protected val drainListeners = mutableListOf<suspend () -> Unit>()
    protected val finishListeners = mutableListOf<suspend () -> Unit>()
    protected val errorListeners = mutableListOf<(Throwable) -> Unit>()
    protected val closeListeners = mutableListOf<() -> Unit>()

    override val isReadable: Boolean
        get() = !disposedState && !endedState

    override val isPaused: Boolean
        get() = pausedState

    override val isEnded: Boolean
        get() = endedState

    override val bytesRead: Long
        get() = totalRead

    override val isWritable: Boolean
        get() = !disposedState && !finishedState && isConnected

    override val isFinished: Boolean
        get() = finishedState

    override val bytesWritten: Long
        get() = totalWritten

    override val isDisposed: Boolean
        get() = disposedState

    override suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Long,
        tls: Boolean
    ): Boolean {
        if (supportedOverride == false || disposedState || host.isBlank() || port !in 1..65535) {
            return false
        }
        remoteHost = host
        remotePort = port
        localPort = nextEphemeralPort++
        isConnected = true
        endedState = false
        finishedState = false
        loopbackGreetings[port]?.let { greeting ->
            if (greeting.isNotEmpty()) {
                pushInboundBytes(greeting.copyOf())
            }
        }
        return true
    }

    /**
     * Enqueues inbound [bytes] into this socket's readable buffer.
     */
    fun pushInboundBytes(bytes: ByteArray) {
        if (bytes.isEmpty() || disposedState) return
        inboundChunks.addLast(ByteBuffer.wrap(bytes.copyOf(), byteOrder = byteOrder))
        endedState = false
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (disposedState || endedState) return null
        val limit = maxBytes.coerceAtLeast(1)
        if (inboundChunks.isEmpty()) return null

        val out = WritableByteBuffer(initialCapacity = minOf(limit, 4096), byteOrder = byteOrder)
        while (out.size < limit && inboundChunks.isNotEmpty()) {
            val head = inboundChunks.removeFirst()
            val bytes = head.remainingByteArray()
            val needed = limit - out.size
            if (bytes.size <= needed) {
                out.writeBytes(bytes)
            } else {
                out.writeBytes(bytes, 0, needed)
                val rest = bytes.copyOfRange(needed, bytes.size)
                inboundChunks.addFirst(ByteBuffer.wrap(rest, byteOrder = byteOrder))
            }
        }
        if (out.isEmpty) return null
        totalRead += out.size
        val result = out.toByteBuffer()
        if (dataListeners.isNotEmpty()) {
            dataListeners.toList().forEach { it(result.duplicate()) }
        }
        return result
    }

    override fun unshift(buffer: ByteBuffer) {
        if (buffer.remaining <= 0) return
        val copy = buffer.remainingByteArray()
        inboundChunks.addFirst(ByteBuffer.wrap(copy, byteOrder = byteOrder))
        totalRead = (totalRead - copy.size).coerceAtLeast(0L)
        endedState = false
    }

    override fun write(buffer: ByteBuffer): Boolean {
        val bytes = buffer.remainingByteArray()
        buffer.position = buffer.limit
        return write(bytes, 0, bytes.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        if (!isWritable) return false
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return true
        val slice = chunk.copyOfRange(offset, offset + length)
        outboundHistory.byteOrder = byteOrder
        outboundHistory.writeBytes(slice)
        totalWritten += slice.size

        if (writeListeners.isNotEmpty()) {
            val bufSlice = ByteBuffer.wrap(slice.copyOf(), byteOrder = byteOrder)
            writeListeners.toList().forEach { it(bufSlice) }
        }

        val handler = loopbackHandlers[remotePort]
        if (handler != null) {
            var responseResult: Result<ByteArray?>? = null
            val block: suspend () -> ByteArray? = { handler(slice) }
            block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome ->
                responseResult = outcome
            })
            val reply = responseResult?.getOrNull()
            if (reply != null && reply.isNotEmpty()) {
                pushInboundBytes(reply)
                if (dataListeners.isNotEmpty()) {
                    val replyBuf = ByteBuffer.wrap(reply.copyOf(), byteOrder = byteOrder)
                    dataListeners.toList().forEach { listener ->
                        val notifyBlock: suspend () -> Unit = { listener(replyBuf.duplicate()) }
                        notifyBlock.startCoroutine(Continuation(EmptyCoroutineContext) {})
                    }
                }
            }
        } else {
            pushInboundBytes(slice)
            if (dataListeners.isNotEmpty()) {
                val replyBuf = ByteBuffer.wrap(slice.copyOf(), byteOrder = byteOrder)
                dataListeners.toList().forEach { listener ->
                    val notifyBlock: suspend () -> Unit = { listener(replyBuf.duplicate()) }
                    notifyBlock.startCoroutine(Continuation(EmptyCoroutineContext) {})
                }
            }
        }

        return outboundHistory.size < highWaterMark
    }

    override fun onData(listener: suspend (ByteBuffer) -> Unit): Readable {
        dataListeners += listener
        return this
    }

    override fun onEnd(listener: suspend () -> Unit): Readable {
        endListeners += listener
        return this
    }

    override fun onWrite(listener: (ByteBuffer) -> Unit): Writable {
        writeListeners += listener
        return this
    }

    override fun onDrain(listener: suspend () -> Unit): Writable {
        drainListeners += listener
        return this
    }

    override fun onFinish(listener: suspend () -> Unit): Writable {
        finishListeners += listener
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Stream {
        errorListeners += listener
        return this
    }

    override fun onClose(listener: () -> Unit): Stream {
        closeListeners += listener
        return this
    }

    override fun pause(): Readable {
        pausedState = true
        return this
    }

    override fun resume(): Readable {
        pausedState = false
        return this
    }

    override suspend fun flush() {
        drainListeners.toList().forEach { it() }
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        if (finishedState) return
        if (finalBuffer != null && finalBuffer.remaining > 0) {
            write(finalBuffer)
        }
        flush()
        finishedState = true
        finishListeners.toList().forEach { it() }
    }

    override suspend fun close() {
        if (!finishedState) {
            end()
        }
        if (!endedState) {
            endedState = true
            endListeners.toList().forEach { it() }
        }
        isConnected = false
        dispose()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        isConnected = false
        endedState = true
        finishedState = true
        inboundChunks.clear()
        closeListeners.toList().forEach { it() }
        dataListeners.clear()
        endListeners.clear()
        writeListeners.clear()
        drainListeners.clear()
        finishListeners.clear()
        errorListeners.clear()
        closeListeners.clear()
    }

    override fun toByteArray(): ByteArray =
        outboundHistory.toByteArray()

    override fun toByteBuffer(): ByteBuffer =
        outboundHistory.toByteBuffer()

    companion object {
        private var nextEphemeralPort = 49152
        private val loopbackHandlers = mutableMapOf<Int, suspend (ByteArray) -> ByteArray?>()
        private val loopbackGreetings = mutableMapOf<Int, ByteArray>()

        /**
         * Registers an in-memory loopback server handler for [port] used during unit tests.
         */
        fun registerLoopbackServer(
            port: Int,
            greeting: ByteArray? = null,
            handler: suspend (ByteArray) -> ByteArray?
        ) {
            loopbackHandlers[port] = handler
            if (greeting != null) {
                loopbackGreetings[port] = greeting.copyOf()
            } else {
                loopbackGreetings.remove(port)
            }
        }

        fun unregisterLoopbackServer(port: Int) {
            loopbackHandlers.remove(port)
            loopbackGreetings.remove(port)
        }

        fun clearLoopbackServers() {
            loopbackHandlers.clear()
            loopbackGreetings.clear()
        }
    }
}
