package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.io.WritableByteBuffer
import com.nexustools.genericui.net.UdpDatagram
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Default in-memory loopback implementation of [UdpSocketNativeImpl] implementing full
 * [com.nexustools.genericui.net.UdpSocket] and [Stream] (`Readable` + `Writable`) semantics.
 */
open class DefaultUdpSocketNativeImpl(
    private val supportedOverride: Boolean? = null,
    private val multicastSupportedOverride: Boolean? = null
) : UdpSocketNativeImpl {

    override var isBound: Boolean = false
        protected set

    override var localAddress: String? = null
        protected set

    override var localPort: Int = 0
        protected set

    override var remoteHost: String? = null
    override var remotePort: Int = 0
    override var broadcastEnabled: Boolean = false

    override var byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    override var highWaterMark: Int = 65536

    private val joinedMulticastGroups = mutableSetOf<String>()
    private val receiveQueue = ArrayDeque<UdpDatagram>()
    private val pushbackQueue = ArrayDeque<ByteBuffer>()
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
        get() = !disposedState && !finishedState

    override val isFinished: Boolean
        get() = finishedState

    override val bytesWritten: Long
        get() = totalWritten

    override val isDisposed: Boolean
        get() = disposedState

    override fun supportsMulticast(): Boolean = multicastSupportedOverride ?: (supportedOverride ?: true)

    override suspend fun bind(localHost: String, localPort: Int): Boolean {
        if (supportedOverride == false || disposedState || localPort !in 0..65535) return false
        val assignedPort = if (localPort == 0) nextEphemeralPort++ else localPort
        this.localAddress = localHost
        this.localPort = assignedPort
        this.isBound = true
        this.endedState = false
        this.finishedState = false
        boundSockets[assignedPort] = this
        return true
    }

    override suspend fun connect(remoteHost: String, remotePort: Int): Boolean {
        if (supportedOverride == false || disposedState || remoteHost.isBlank() || remotePort !in 1..65535) return false
        if (!isBound) {
            bind("0.0.0.0", 0)
        }
        this.remoteHost = remoteHost
        this.remotePort = remotePort
        return true
    }

    override suspend fun joinMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        if (!supportsMulticast() || disposedState || multicastAddress.isBlank()) return false
        if (!isBound) {
            bind("0.0.0.0", 0)
        }
        joinedMulticastGroups.add(multicastAddress)
        multicastSubscribers.getOrPut(multicastAddress) { mutableSetOf() }.add(this)
        return true
    }

    override suspend fun leaveMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        val removed = joinedMulticastGroups.remove(multicastAddress)
        multicastSubscribers[multicastAddress]?.remove(this)
        return removed
    }

    override suspend fun send(bytes: ByteArray, host: String, port: Int): Int {
        if (supportedOverride == false || disposedState || port !in 1..65535) return -1
        if (!isBound) {
            bind("0.0.0.0", 0)
        }
        dispatchDatagram(bytes.copyOf(), host, port)
        return bytes.size
    }

    private fun dispatchDatagram(payload: ByteArray, host: String, port: Int) {
        val datagram = UdpDatagram(
            data = payload,
            remoteAddress = localAddress ?: "127.0.0.1",
            remotePort = localPort
        )
        boundSockets[port]?.enqueueDatagram(datagram)
        multicastSubscribers[host]?.forEach { subscriber ->
            if (subscriber !== boundSockets[port]) {
                subscriber.enqueueDatagram(datagram)
            }
        }
    }

    internal fun enqueueDatagram(datagram: UdpDatagram) {
        if (disposedState) return
        receiveQueue.addLast(datagram)
        endedState = false
        if (dataListeners.isNotEmpty()) {
            val buf = ByteBuffer.wrap(datagram.data.copyOf(), byteOrder = byteOrder)
            dataListeners.toList().forEach { listener ->
                val notifyBlock: suspend () -> Unit = { listener(buf.duplicate()) }
                notifyBlock.startCoroutine(Continuation(EmptyCoroutineContext) {})
            }
        }
    }

    override suspend fun receive(maxBytes: Int, timeoutMs: Long): UdpDatagram? {
        if (supportedOverride == false || !isBound || disposedState || receiveQueue.isEmpty()) return null
        val datagram = receiveQueue.removeFirst()
        return if (datagram.data.size > maxBytes) {
            datagram.copy(data = datagram.data.copyOf(maxBytes))
        } else {
            datagram
        }
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (disposedState || endedState) return null
        val limit = maxBytes.coerceAtLeast(1)

        if (pushbackQueue.isNotEmpty()) {
            val head = pushbackQueue.removeFirst()
            val bytes = head.remainingByteArray()
            if (bytes.size <= limit) {
                totalRead += bytes.size
                return ByteBuffer.wrap(bytes, byteOrder = byteOrder)
            }
            val taken = bytes.copyOfRange(0, limit)
            val rest = bytes.copyOfRange(limit, bytes.size)
            pushbackQueue.addFirst(ByteBuffer.wrap(rest, byteOrder = byteOrder))
            totalRead += taken.size
            return ByteBuffer.wrap(taken, byteOrder = byteOrder)
        }

        val dgram = receive(limit, 0L) ?: return null
        totalRead += dgram.data.size
        return ByteBuffer.wrap(dgram.data, byteOrder = byteOrder)
    }

    override fun unshift(buffer: ByteBuffer) {
        if (buffer.remaining <= 0) return
        val copy = buffer.remainingByteArray()
        pushbackQueue.addFirst(ByteBuffer.wrap(copy, byteOrder = byteOrder))
        totalRead = (totalRead - copy.size).coerceAtLeast(0L)
        endedState = false
    }

    override fun write(buffer: ByteBuffer): Boolean {
        val bytes = buffer.remainingByteArray()
        buffer.position = buffer.limit
        return write(bytes, 0, bytes.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        if (supportedOverride == false || !isWritable) return false
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return true
        if (!isBound) {
            val assignedPort = nextEphemeralPort++
            localAddress = "0.0.0.0"
            localPort = assignedPort
            isBound = true
            boundSockets[assignedPort] = this
        }
        val slice = chunk.copyOfRange(offset, offset + length)
        outboundHistory.byteOrder = byteOrder
        outboundHistory.writeBytes(slice)
        totalWritten += slice.size

        if (remotePort in 1..65535) {
            dispatchDatagram(slice, remoteHost ?: "127.0.0.1", remotePort)
        }

        if (writeListeners.isNotEmpty()) {
            val bufSlice = ByteBuffer.wrap(slice.copyOf(), byteOrder = byteOrder)
            writeListeners.toList().forEach { it(bufSlice) }
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
        dispose()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        if (localPort != 0 && boundSockets[localPort] === this) {
            boundSockets.remove(localPort)
        }
        joinedMulticastGroups.forEach { group ->
            multicastSubscribers[group]?.remove(this)
        }
        joinedMulticastGroups.clear()
        receiveQueue.clear()
        pushbackQueue.clear()
        isBound = false
        localAddress = null
        localPort = 0
        endedState = true
        finishedState = true
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
        private var nextEphemeralPort = 52000
        private val boundSockets = mutableMapOf<Int, DefaultUdpSocketNativeImpl>()
        private val multicastSubscribers = mutableMapOf<String, MutableSet<DefaultUdpSocketNativeImpl>>()

        fun clearLoopbackEndpoints() {
            boundSockets.clear()
            multicastSubscribers.clear()
        }
    }
}
