package com.nexustools.genericui.io

import com.nexustools.genericui.core.Disposable

/**
 * Node.js-inspired asynchronous readable binary/text stream interface.
 *
 * All reads operate on cross-platform [ByteBuffer] instances (modeled after Node.js `Buffer`),
 * with endian-aware binary primitive getters (`readByte`, `readShort`, `readInt`, `readLong`,
 * `readFloat`, `readDouble`, etc.) as well as `ByteArray` and decoded text helpers.
 */
interface Readable : Disposable {
    val isReadable: Boolean
    val isPaused: Boolean
    val isEnded: Boolean
    val bytesRead: Long
    var byteOrder: ByteOrder

    /**
     * Reads up to [maxBytes] bytes as a [ByteBuffer], or returns `null` when EOF is reached.
     */
    suspend fun read(maxBytes: Int = 65536): ByteBuffer?

    /**
     * Reads up to [maxBytes] bytes as a raw [ByteArray], or returns `null` when EOF is reached.
     */
    suspend fun readBytes(maxBytes: Int = 65536): ByteArray? = read(maxBytes)?.toByteArray()

    /**
     * Alias for [read] returning a [ByteBuffer].
     */
    suspend fun readBuffer(maxBytes: Int = 65536): ByteBuffer? = read(maxBytes)

    /**
     * Reads up to [maxBytes] bytes and decodes them as a text [String] using [encoding].
     */
    suspend fun readText(maxBytes: Int = 65536, encoding: String = "utf8"): String? =
        read(maxBytes)?.toString(encoding)

    /**
     * Reads 1 byte from the stream, or returns `null` when EOF is reached.
     */
    suspend fun readByteOrNull(): Byte? {
        val chunk = read(1) ?: return null
        if (!chunk.hasRemaining) return null
        val b = chunk.readByte()
        if (chunk.hasRemaining) {
            unshift(chunk)
        }
        return b
    }

    /**
     * Attempts to read [byteCount] bytes from the stream into a [ByteBuffer], or returns `null`
     * if EOF is reached before [byteCount] bytes are available.
     */
    suspend fun tryReadFully(byteCount: Int): ByteBuffer? {
        require(byteCount >= 0) { "byteCount must be >= 0: $byteCount" }
        if (byteCount == 0) return ByteBuffer.empty()
        val out = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val chunk = read(byteCount - offset) ?: return null
            val bytes = chunk.remainingByteArray()
            if (bytes.isEmpty()) return null
            val toCopy = minOf(bytes.size, byteCount - offset)
            bytes.copyInto(out, destinationOffset = offset, startIndex = 0, endIndex = toCopy)
            if (toCopy < bytes.size) {
                unshift(ByteBuffer.wrap(bytes, toCopy, bytes.size - toCopy, byteOrder))
            }
            offset += toCopy
        }
        return ByteBuffer.wrap(out, byteOrder = byteOrder)
    }

    /**
     * Reads exactly [byteCount] bytes from the stream into a [ByteBuffer], or throws
     * [IllegalStateException] if EOF is reached before [byteCount] bytes are read.
     */
    suspend fun readFully(byteCount: Int): ByteBuffer =
        tryReadFully(byteCount)
            ?: throw IllegalStateException("Unexpected end of stream: expected $byteCount bytes")

    suspend fun readByte(): Byte = readFully(1).readByte(0)

    suspend fun readUByte(): UByte = readFully(1).readUByte(0)

    suspend fun readBoolean(): Boolean = readFully(1).readBoolean(0)

    suspend fun readShort(order: ByteOrder = byteOrder): Short = readFully(2).readShort(0, order)

    suspend fun readUShort(order: ByteOrder = byteOrder): UShort = readFully(2).readUShort(0, order)

    suspend fun readInt(order: ByteOrder = byteOrder): Int = readFully(4).readInt(0, order)

    suspend fun readUInt(order: ByteOrder = byteOrder): UInt = readFully(4).readUInt(0, order)

    suspend fun readLong(order: ByteOrder = byteOrder): Long = readFully(8).readLong(0, order)

    suspend fun readULong(order: ByteOrder = byteOrder): ULong = readFully(8).readULong(0, order)

    suspend fun readFloat(order: ByteOrder = byteOrder): Float = readFully(4).readFloat(0, order)

    suspend fun readDouble(order: ByteOrder = byteOrder): Double = readFully(8).readDouble(0, order)

    suspend fun readUnsignedInt(byteCount: Int, order: ByteOrder = byteOrder): Int =
        readFully(byteCount).readUnsignedInt(0, byteCount, order)

    suspend fun readString(byteCount: Int, encoding: String = "utf8"): String =
        readFully(byteCount).toString(encoding)

    /**
     * Reads all remaining bytes from this stream and returns a single combined [ByteBuffer].
     */
    suspend fun readAllBuffer(): ByteBuffer {
        val out = WritableByteBuffer(initialCapacity = 256, byteOrder = byteOrder)
        while (true) {
            val chunk = read() ?: break
            if (chunk.remaining > 0) {
                out.write(chunk)
            }
        }
        return out.toByteBuffer()
    }

    /**
     * Reads all remaining bytes from this stream as a [ByteArray].
     */
    suspend fun readAllBytes(): ByteArray = readAllBuffer().toByteArray()

    /**
     * Reads all remaining bytes from this stream and decodes them as a [String].
     */
    suspend fun readAllText(encoding: String = "utf8"): String =
        readAllBuffer().toString(encoding)

    /**
     * Iterates over each chunk of data until the stream ends.
     */
    suspend fun forEachChunk(chunkSize: Int = 65536, consumer: suspend (ByteBuffer) -> Unit) {
        while (true) {
            val chunk = read(chunkSize) ?: break
            if (chunk.remaining > 0) {
                consumer(chunk)
            }
        }
    }

    /**
     * Reads the stream line-by-line (splitting on `\n` or `\r\n`) and invokes [consumer] for each line.
     */
    suspend fun forEachLine(encoding: String = "utf8", consumer: suspend (String) -> Unit) {
        val carry = StringBuilder()
        forEachChunk { chunk ->
            carry.append(chunk.toString(encoding))
            var newlineIdx = carry.indexOf('\n')
            while (newlineIdx >= 0) {
                var line = carry.substring(0, newlineIdx)
                if (line.endsWith('\r')) {
                    line = line.substring(0, line.length - 1)
                }
                consumer(line)
                carry.deleteRange(0, newlineIdx + 1)
                newlineIdx = carry.indexOf('\n')
            }
        }
        if (carry.isNotEmpty()) {
            var tail = carry.toString()
            if (tail.endsWith('\r')) {
                tail = tail.substring(0, tail.length - 1)
            }
            consumer(tail)
        }
    }

    /**
     * Pipes all data from this [Readable] stream into [destination], respecting backpressure.
     *
     * @param destination Target writable stream.
     * @param endDestination Whether to call [Writable.end] on [destination] when this stream ends.
     * @param chunkSize Read buffer size in bytes.
     * @return Total number of bytes piped.
     */
    suspend fun pipe(
        destination: Writable,
        endDestination: Boolean = true,
        chunkSize: Int = 65536
    ): Long {
        var totalPiped = 0L
        while (true) {
            val chunk = read(chunkSize) ?: break
            if (chunk.remaining > 0) {
                val len = chunk.remaining
                destination.write(chunk)
                totalPiped += len
            }
        }
        destination.flush()
        if (endDestination) {
            destination.end()
        }
        return totalPiped
    }

    fun onData(listener: suspend (ByteBuffer) -> Unit): Readable
    fun onEnd(listener: suspend () -> Unit): Readable
    fun onError(listener: (Throwable) -> Unit): Readable
    fun onClose(listener: () -> Unit): Readable

    fun pause(): Readable
    fun resume(): Readable

    /**
     * Pushes a [ByteBuffer] chunk back to the front of the read queue.
     */
    fun unshift(buffer: ByteBuffer)

    /**
     * Pushes a raw [ByteArray] chunk back to the front of the read queue.
     */
    fun unshift(chunk: ByteArray) = unshift(ByteBuffer.wrap(chunk, byteOrder = byteOrder))

    suspend fun close()

    companion object {
        fun fromBytes(bytes: ByteArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): Readable =
            ReadableByteBuffer(bytes.copyOf(), byteOrder = byteOrder)

        fun fromBuffer(buffer: ByteBuffer): Readable =
            ReadableByteBuffer(buffer, buffer.position, buffer.byteOrder)

        fun fromText(text: String, encoding: String = "utf8"): Readable =
            fromBuffer(ByteBuffer.fromString(text, encoding))

        fun empty(): Readable =
            ReadableByteBuffer(ByteBuffer.empty())
    }
}
