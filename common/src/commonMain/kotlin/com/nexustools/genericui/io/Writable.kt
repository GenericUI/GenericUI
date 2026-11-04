package com.nexustools.genericui.io

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.nativeimpl.io.MemoryWritableNativeImpl

/**
 * Node.js-inspired writable binary/text stream interface.
 *
 * All writes operate on cross-platform [ByteBuffer] instances (modeled after Node.js `Buffer`),
 * with endian-aware binary primitive setters (`writeByte`, `writeShort`, `writeInt`, `writeLong`,
 * `writeFloat`, `writeDouble`, etc.) as well as `ByteArray` and UTF-8/UTF-16/Latin-1/Hex/Base64
 * text helpers.
 */
interface Writable : Disposable {
    val isWritable: Boolean
    val isFinished: Boolean
    val bytesWritten: Long
    var highWaterMark: Int
    var byteOrder: ByteOrder

    /**
     * Writes the remaining readable bytes of [buffer] to the stream.
     *
     * @return `true` if the internal buffer is below [highWaterMark], or `false` if backpressure
     *         should be respected before writing additional chunks.
     */
    fun write(buffer: ByteBuffer): Boolean

    /**
     * Writes [length] bytes from [chunk] starting at [offset].
     */
    fun write(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size - offset): Boolean =
        write(ByteBuffer.wrap(chunk, offset, length, byteOrder))

    fun writeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean =
        write(bytes, offset, length)

    fun writeBuffer(buffer: ByteBuffer): Boolean = write(buffer)

    /**
     * Encodes [text] using [encoding] and writes it to the stream.
     */
    fun writeText(text: String, encoding: String = "utf8"): Boolean =
        write(ByteBuffer.fromString(text, encoding, byteOrder))

    fun writeString(text: String, encoding: String = "utf8"): Boolean =
        writeText(text, encoding)

    /**
     * Writes [line] followed by a newline (`\n`).
     */
    fun writeLine(line: String = "", encoding: String = "utf8"): Boolean =
        writeText("$line\n", encoding)

    fun writeByte(value: Byte): Boolean =
        write(ByteBuffer.allocate(1, byteOrder).writeByte(0, value))

    fun writeByte(value: Int): Boolean =
        writeByte(value.toByte())

    fun writeUByte(value: UByte): Boolean =
        writeByte(value.toByte())

    fun writeBoolean(value: Boolean): Boolean =
        writeByte(if (value) 1 else 0)

    fun writeShort(value: Short, order: ByteOrder = byteOrder): Boolean =
        write(ByteBuffer.allocate(2, order).writeShort(0, value, order))

    fun writeUShort(value: UShort, order: ByteOrder = byteOrder): Boolean =
        writeShort(value.toShort(), order)

    fun writeInt(value: Int, order: ByteOrder = byteOrder): Boolean =
        write(ByteBuffer.allocate(4, order).writeInt(0, value, order))

    fun writeUInt(value: UInt, order: ByteOrder = byteOrder): Boolean =
        writeInt(value.toInt(), order)

    fun writeLong(value: Long, order: ByteOrder = byteOrder): Boolean =
        write(ByteBuffer.allocate(8, order).writeLong(0, value, order))

    fun writeULong(value: ULong, order: ByteOrder = byteOrder): Boolean =
        writeLong(value.toLong(), order)

    fun writeFloat(value: Float, order: ByteOrder = byteOrder): Boolean =
        writeInt(value.toRawBits(), order)

    fun writeDouble(value: Double, order: ByteOrder = byteOrder): Boolean =
        writeLong(value.toRawBits(), order)

    fun writeUnsignedInt(value: Int, byteCount: Int, order: ByteOrder = byteOrder): Boolean {
        require(byteCount in 1..4) { "byteCount must be in 1..4, got $byteCount" }
        return write(ByteBuffer.allocate(byteCount, order).writeUnsignedInt(0, value, byteCount, order))
    }

    fun onWrite(listener: (ByteBuffer) -> Unit): Writable
    fun onDrain(listener: suspend () -> Unit): Writable
    fun onFinish(listener: suspend () -> Unit): Writable
    fun onError(listener: (Throwable) -> Unit): Writable
    fun onClose(listener: () -> Unit): Writable

    suspend fun flush()

    /**
     * Signals that no more data will be written, optionally writing a final [finalBuffer] first.
     */
    suspend fun end(finalBuffer: ByteBuffer? = null)

    suspend fun end(finalChunk: ByteArray) =
        end(ByteBuffer.wrap(finalChunk, byteOrder = byteOrder))

    suspend fun endText(finalText: String, encoding: String = "utf8") =
        end(ByteBuffer.fromString(finalText, encoding, byteOrder))

    suspend fun close()

    /**
     * Returns all bytes written to an in-memory [Writable] stream (empty for purely sink streams).
     */
    fun toByteArray(): ByteArray = ByteArray(0)

    /**
     * Returns all bytes written to an in-memory [Writable] stream as a [ByteBuffer].
     */
    fun toByteBuffer(): ByteBuffer = ByteBuffer.wrap(toByteArray(), byteOrder = byteOrder)

    companion object {
        fun toMemory(onCommit: (suspend (ByteArray) -> Unit)? = null): Writable =
            MemoryWritableNativeImpl(onCommit = onCommit)

        fun nullSink(): Writable =
            MemoryWritableNativeImpl(onWriteChunk = { _, _, len -> len })
    }
}
