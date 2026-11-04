package com.nexustools.genericui.io

/**
 * Dynamically expanding sequential binary writer implementing [Writable].
 *
 * Unlike [ByteBuffer], which writes at explicit byte indices, [WritableByteBuffer] appends
 * bytes, endian-aware primitives, strings, and [ByteBuffer] slices sequentially to the end
 * and automatically expands its internal [ByteArray] with zero temporary buffer allocations.
 */
class WritableByteBuffer(
    initialCapacity: Int = 256,
    override var byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : Writable {
    private var backing: ByteArray = ByteArray(initialCapacity.coerceAtLeast(16))
    private var finished: Boolean = false
    private var disposed: Boolean = false

    private val writeListeners = mutableListOf<(ByteBuffer) -> Unit>()
    private val drainListeners = mutableListOf<suspend () -> Unit>()
    private val finishListeners = mutableListOf<suspend () -> Unit>()
    private val errorListeners = mutableListOf<(Throwable) -> Unit>()
    private val closeListeners = mutableListOf<() -> Unit>()

    var size: Int = 0
        private set

    val capacity: Int
        get() = backing.size

    val isEmpty: Boolean
        get() = size == 0

    val isNotEmpty: Boolean
        get() = size > 0

    override val isWritable: Boolean
        get() = !disposed && !finished

    override val isFinished: Boolean
        get() = finished

    override val bytesWritten: Long
        get() = size.toLong()

    override var highWaterMark: Int = 65536

    override val isDisposed: Boolean
        get() = disposed

    fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= backing.size) return
        var newCap = backing.size.coerceAtLeast(16)
        while (newCap < requiredCapacity) {
            newCap = newCap * 2
        }
        backing = backing.copyOf(newCap)
    }

    private fun checkWritable() {
        check(!disposed) { "WritableByteBuffer is disposed" }
        check(!finished) { "WritableByteBuffer has already ended" }
    }

    private fun notifyWriteIfNeeded(startOffset: Int, length: Int) {
        if (writeListeners.isNotEmpty() && length > 0) {
            val slice = ByteBuffer.wrap(backing.copyOfRange(startOffset, startOffset + length), byteOrder = byteOrder)
            writeListeners.toList().forEach { it(slice) }
        }
    }

    override fun write(buffer: ByteBuffer): Boolean {
        checkWritable()
        val len = buffer.remaining
        if (len <= 0) return size < highWaterMark
        val start = size
        ensureCapacity(size + len)
        buffer.internalArray().copyInto(
            destination = backing,
            destinationOffset = size,
            startIndex = buffer.position,
            endIndex = buffer.limit
        )
        buffer.position = buffer.limit
        size += len
        notifyWriteIfNeeded(start, len)
        return size < highWaterMark
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        checkWritable()
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return size < highWaterMark
        val start = size
        ensureCapacity(size + length)
        chunk.copyInto(
            destination = backing,
            destinationOffset = size,
            startIndex = offset,
            endIndex = offset + length
        )
        size += length
        notifyWriteIfNeeded(start, length)
        return size < highWaterMark
    }

    override fun writeBytes(bytes: ByteArray, offset: Int, length: Int): Boolean =
        write(bytes, offset, length)

    override fun writeBuffer(buffer: ByteBuffer): Boolean =
        write(buffer)

    override fun writeByte(value: Byte): Boolean {
        checkWritable()
        val start = size
        ensureCapacity(size + 1)
        backing[size++] = value
        notifyWriteIfNeeded(start, 1)
        return size < highWaterMark
    }

    override fun writeByte(value: Int): Boolean =
        writeByte(value.toByte())

    override fun writeUByte(value: UByte): Boolean =
        writeByte(value.toByte())

    override fun writeBoolean(value: Boolean): Boolean =
        writeByte(if (value) 1.toByte() else 0.toByte())

    override fun writeShort(value: Short, order: ByteOrder): Boolean {
        checkWritable()
        val start = size
        ensureCapacity(size + 2)
        val v = value.toInt()
        if (order == ByteOrder.BIG_ENDIAN) {
            backing[size++] = ((v ushr 8) and 0xFF).toByte()
            backing[size++] = (v and 0xFF).toByte()
        } else {
            backing[size++] = (v and 0xFF).toByte()
            backing[size++] = ((v ushr 8) and 0xFF).toByte()
        }
        notifyWriteIfNeeded(start, 2)
        return size < highWaterMark
    }

    override fun writeUShort(value: UShort, order: ByteOrder): Boolean =
        writeShort(value.toShort(), order)

    override fun writeInt(value: Int, order: ByteOrder): Boolean {
        checkWritable()
        val start = size
        ensureCapacity(size + 4)
        if (order == ByteOrder.BIG_ENDIAN) {
            backing[size++] = ((value ushr 24) and 0xFF).toByte()
            backing[size++] = ((value ushr 16) and 0xFF).toByte()
            backing[size++] = ((value ushr 8) and 0xFF).toByte()
            backing[size++] = (value and 0xFF).toByte()
        } else {
            backing[size++] = (value and 0xFF).toByte()
            backing[size++] = ((value ushr 8) and 0xFF).toByte()
            backing[size++] = ((value ushr 16) and 0xFF).toByte()
            backing[size++] = ((value ushr 24) and 0xFF).toByte()
        }
        notifyWriteIfNeeded(start, 4)
        return size < highWaterMark
    }

    override fun writeUInt(value: UInt, order: ByteOrder): Boolean =
        writeInt(value.toInt(), order)

    override fun writeLong(value: Long, order: ByteOrder): Boolean {
        checkWritable()
        val start = size
        ensureCapacity(size + 8)
        if (order == ByteOrder.BIG_ENDIAN) {
            for (shift in 56 downTo 0 step 8) {
                backing[size++] = ((value ushr shift) and 0xFFL).toByte()
            }
        } else {
            for (shift in 0..56 step 8) {
                backing[size++] = ((value ushr shift) and 0xFFL).toByte()
            }
        }
        notifyWriteIfNeeded(start, 8)
        return size < highWaterMark
    }

    override fun writeULong(value: ULong, order: ByteOrder): Boolean =
        writeLong(value.toLong(), order)

    override fun writeFloat(value: Float, order: ByteOrder): Boolean =
        writeInt(value.toRawBits(), order)

    override fun writeDouble(value: Double, order: ByteOrder): Boolean =
        writeLong(value.toRawBits(), order)

    override fun writeUnsignedInt(value: Int, byteCount: Int, order: ByteOrder): Boolean {
        checkWritable()
        require(byteCount in 1..4) { "byteCount must be in 1..4, got $byteCount" }
        val start = size
        ensureCapacity(size + byteCount)
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in (byteCount - 1) downTo 0) {
                backing[size++] = ((value ushr (i * 8)) and 0xFF).toByte()
            }
        } else {
            for (i in 0 until byteCount) {
                backing[size++] = ((value ushr (i * 8)) and 0xFF).toByte()
            }
        }
        notifyWriteIfNeeded(start, byteCount)
        return size < highWaterMark
    }

    override fun writeText(text: String, encoding: String): Boolean {
        val encoded = ByteBuffer.encodeString(text, encoding)
        return write(encoded, 0, encoded.size)
    }

    override fun writeString(text: String, encoding: String): Boolean =
        writeText(text, encoding)

    override fun writeLine(line: String, encoding: String): Boolean =
        writeText("$line\n", encoding)

    fun clear(): WritableByteBuffer {
        size = 0
        finished = false
        return this
    }

    fun reset(): WritableByteBuffer = clear()

    override fun onWrite(listener: (ByteBuffer) -> Unit): WritableByteBuffer {
        writeListeners += listener
        return this
    }

    override fun onDrain(listener: suspend () -> Unit): WritableByteBuffer {
        drainListeners += listener
        return this
    }

    override fun onFinish(listener: suspend () -> Unit): WritableByteBuffer {
        finishListeners += listener
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): WritableByteBuffer {
        errorListeners += listener
        return this
    }

    override fun onClose(listener: () -> Unit): WritableByteBuffer {
        closeListeners += listener
        return this
    }

    override suspend fun flush() {
        drainListeners.toList().forEach { it() }
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        if (finished) return
        if (finalBuffer != null && finalBuffer.remaining > 0) {
            write(finalBuffer)
        }
        finished = true
        finishListeners.toList().forEach { it() }
    }

    override suspend fun close() {
        if (!finished) {
            end()
        }
        dispose()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        finished = true
        closeListeners.toList().forEach { it() }
        writeListeners.clear()
        drainListeners.clear()
        finishListeners.clear()
        errorListeners.clear()
        closeListeners.clear()
    }

    override fun toByteArray(): ByteArray =
        backing.copyOfRange(0, size)

    override fun toByteBuffer(): ByteBuffer =
        ByteBuffer.wrap(toByteArray(), byteOrder = byteOrder)

    fun toString(encoding: String = "utf8"): String =
        toByteBuffer().toString(encoding)

    override fun toString(): String =
        toString("utf8")
}
