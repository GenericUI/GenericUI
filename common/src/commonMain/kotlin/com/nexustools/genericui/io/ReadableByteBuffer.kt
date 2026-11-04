package com.nexustools.genericui.io

/**
 * Sequential position-tracking reader over a [ByteBuffer] implementing [Readable].
 *
 * Keeps track of [position] as bytes, primitives, and strings are read, supporting both
 * asynchronous [Readable] stream consumption (`read`, `readInt`, `pipe`, `forEachChunk`, etc.)
 * and synchronous cursor reads (`nextByte`, `nextShort`, `nextInt`, `nextLong`, `nextFloat`,
 * `nextDouble`, `nextString`, etc.).
 */
open class ReadableByteBuffer(
    val buffer: ByteBuffer,
    position: Int = 0,
    override var byteOrder: ByteOrder = buffer.byteOrder
) : Readable {

    constructor(
        bytes: ByteArray,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(ByteBuffer.wrap(bytes, byteOrder = byteOrder), 0, byteOrder)

    private val pushbackQueue = ArrayDeque<ByteBuffer>()
    private var pausedState: Boolean = false
    private var endedState: Boolean = false
    private var disposedState: Boolean = false
    private var totalBytesRead: Long = 0L

    protected val dataListeners = mutableListOf<suspend (ByteBuffer) -> Unit>()
    protected val endListeners = mutableListOf<suspend () -> Unit>()
    protected val errorListeners = mutableListOf<(Throwable) -> Unit>()
    protected val closeListeners = mutableListOf<() -> Unit>()

    var position: Int = position.coerceIn(0, buffer.size)
        set(value) {
            field = value.coerceIn(0, buffer.size)
            if (field < buffer.size) {
                endedState = false
            }
        }

    val remaining: Int
        get() = pushbackBytesCount() + (buffer.size - position).coerceAtLeast(0)

    val hasRemaining: Boolean
        get() = pushbackQueue.isNotEmpty() || position < buffer.size

    override val isReadable: Boolean
        get() = !disposedState && !endedState

    override val isPaused: Boolean
        get() = pausedState

    override val isEnded: Boolean
        get() = endedState

    override val bytesRead: Long
        get() = totalBytesRead

    override val isDisposed: Boolean
        get() = disposedState

    fun rewind(): ReadableByteBuffer {
        position = 0
        endedState = false
        return this
    }

    private fun pushbackBytesCount(): Int {
        var sum = 0
        for (buf in pushbackQueue) {
            sum += buf.remaining
        }
        return sum
    }

    /**
     * Synchronous cursor read of 1 signed [Byte] at [position], advancing [position] by 1.
     */
    fun nextByte(): Byte {
        val value = buffer.readByte(position)
        position += 1
        totalBytesRead += 1
        return value
    }

    /**
     * Synchronous cursor read of 1 unsigned [UByte] at [position], advancing [position] by 1.
     */
    fun nextUByte(): UByte {
        val value = buffer.readUByte(position)
        position += 1
        totalBytesRead += 1
        return value
    }

    /**
     * Synchronous cursor read of 1 [Boolean] byte at [position], advancing [position] by 1.
     */
    fun nextBoolean(): Boolean {
        val value = buffer.readBoolean(position)
        position += 1
        totalBytesRead += 1
        return value
    }

    /**
     * Synchronous cursor read of a 16-bit [Short] at [position], advancing [position] by 2.
     */
    fun nextShort(order: ByteOrder = byteOrder): Short {
        val value = buffer.readShort(position, order)
        position += 2
        totalBytesRead += 2
        return value
    }

    /**
     * Synchronous cursor read of a 16-bit [UShort] at [position], advancing [position] by 2.
     */
    fun nextUShort(order: ByteOrder = byteOrder): UShort {
        val value = buffer.readUShort(position, order)
        position += 2
        totalBytesRead += 2
        return value
    }

    /**
     * Synchronous cursor read of a 32-bit [Int] at [position], advancing [position] by 4.
     */
    fun nextInt(order: ByteOrder = byteOrder): Int {
        val value = buffer.readInt(position, order)
        position += 4
        totalBytesRead += 4
        return value
    }

    /**
     * Synchronous cursor read of a 32-bit [UInt] at [position], advancing [position] by 4.
     */
    fun nextUInt(order: ByteOrder = byteOrder): UInt {
        val value = buffer.readUInt(position, order)
        position += 4
        totalBytesRead += 4
        return value
    }

    /**
     * Synchronous cursor read of a 64-bit [Long] at [position], advancing [position] by 8.
     */
    fun nextLong(order: ByteOrder = byteOrder): Long {
        val value = buffer.readLong(position, order)
        position += 8
        totalBytesRead += 8
        return value
    }

    /**
     * Synchronous cursor read of a 64-bit [ULong] at [position], advancing [position] by 8.
     */
    fun nextULong(order: ByteOrder = byteOrder): ULong {
        val value = buffer.readULong(position, order)
        position += 8
        totalBytesRead += 8
        return value
    }

    /**
     * Synchronous cursor read of a 32-bit IEEE-754 [Float] at [position], advancing [position] by 4.
     */
    fun nextFloat(order: ByteOrder = byteOrder): Float {
        val value = buffer.readFloat(position, order)
        position += 4
        totalBytesRead += 4
        return value
    }

    /**
     * Synchronous cursor read of a 64-bit IEEE-754 [Double] at [position], advancing [position] by 8.
     */
    fun nextDouble(order: ByteOrder = byteOrder): Double {
        val value = buffer.readDouble(position, order)
        position += 8
        totalBytesRead += 8
        return value
    }

    /**
     * Synchronous cursor read of a 1..4 byte unsigned integer at [position], advancing [position] by [byteCount].
     */
    fun nextUnsignedInt(byteCount: Int, order: ByteOrder = byteOrder): Int {
        val value = buffer.readUnsignedInt(position, byteCount, order)
        position += byteCount
        totalBytesRead += byteCount
        return value
    }

    /**
     * Synchronous cursor read of [byteCount] bytes decoded as [encoding], advancing [position].
     */
    fun nextString(byteCount: Int, encoding: String = "utf8"): String {
        val end = (position + byteCount.coerceAtLeast(0)).coerceAtMost(buffer.size)
        val result = buffer.toString(encoding, position, end)
        val consumed = end - position
        position = end
        totalBytesRead += consumed
        return result
    }

    /**
     * Synchronous cursor read of up to [byteCount] bytes as a [ByteArray], advancing [position].
     */
    fun nextBytes(byteCount: Int): ByteArray {
        val end = (position + byteCount.coerceAtLeast(0)).coerceAtMost(buffer.size)
        val slice = buffer.toByteArray(position, end)
        val consumed = end - position
        position = end
        totalBytesRead += consumed
        return slice
    }

    /**
     * Hook for reading up to [maxBytes] from the underlying source when the pushback queue is empty.
     */
    protected open suspend fun readChunkFromSource(maxBytes: Int): ByteBuffer? {
        val avail = buffer.size - position
        if (avail <= 0) return null
        val count = minOf(maxBytes, avail)
        val start = position
        position += count
        return ByteBuffer.wrap(buffer.toByteArray(start, start + count), byteOrder = byteOrder)
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (disposedState || endedState) return null
        val limit = maxBytes.coerceAtLeast(1)

        if (pushbackQueue.isNotEmpty()) {
            val head = pushbackQueue.removeFirst()
            val bytes = head.remainingByteArray()
            if (bytes.size <= limit) {
                totalBytesRead += bytes.size
                val out = ByteBuffer.wrap(bytes, byteOrder = byteOrder)
                notifyDataListeners(out)
                return out
            }
            val taken = bytes.copyOfRange(0, limit)
            val rest = bytes.copyOfRange(limit, bytes.size)
            pushbackQueue.addFirst(ByteBuffer.wrap(rest, byteOrder = byteOrder))
            totalBytesRead += taken.size
            val out = ByteBuffer.wrap(taken, byteOrder = byteOrder)
            notifyDataListeners(out)
            return out
        }

        val chunk = try {
            readChunkFromSource(limit)
        } catch (t: Throwable) {
            notifyErrorListeners(t)
            throw t
        }

        if (chunk == null || chunk.remaining == 0) {
            markEnded()
            return null
        }

        val chunkBytes = chunk.remainingByteArray()
        return if (chunkBytes.size <= limit) {
            totalBytesRead += chunkBytes.size
            val out = ByteBuffer.wrap(chunkBytes, byteOrder = byteOrder)
            notifyDataListeners(out)
            out
        } else {
            val taken = chunkBytes.copyOfRange(0, limit)
            val rest = chunkBytes.copyOfRange(limit, chunkBytes.size)
            pushbackQueue.addFirst(ByteBuffer.wrap(rest, byteOrder = byteOrder))
            totalBytesRead += taken.size
            val out = ByteBuffer.wrap(taken, byteOrder = byteOrder)
            notifyDataListeners(out)
            out
        }
    }

    protected suspend fun notifyDataListeners(chunk: ByteBuffer) {
        if (dataListeners.isNotEmpty()) {
            dataListeners.toList().forEach { it(chunk.duplicate()) }
        }
    }

    protected suspend fun markEnded() {
        if (!endedState) {
            endedState = true
            endListeners.toList().forEach { it() }
        }
    }

    protected fun notifyErrorListeners(error: Throwable) {
        errorListeners.toList().forEach { it(error) }
    }

    override fun onData(listener: suspend (ByteBuffer) -> Unit): Readable {
        dataListeners += listener
        return this
    }

    override fun onEnd(listener: suspend () -> Unit): Readable {
        endListeners += listener
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Readable {
        errorListeners += listener
        return this
    }

    override fun onClose(listener: () -> Unit): Readable {
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

    override fun unshift(buffer: ByteBuffer) {
        if (buffer.remaining <= 0) return
        val copy = buffer.remainingByteArray()
        pushbackQueue.addFirst(ByteBuffer.wrap(copy, byteOrder = byteOrder))
        totalBytesRead = (totalBytesRead - copy.size).coerceAtLeast(0L)
        endedState = false
    }

    override suspend fun close() {
        dispose()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        endedState = true
        pushbackQueue.clear()
        closeListeners.toList().forEach { it() }
        dataListeners.clear()
        endListeners.clear()
        errorListeners.clear()
        closeListeners.clear()
    }
}
