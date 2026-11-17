package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileReadableNativeImpl
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer as NioByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * Shared JVM and Android (`minSdk >= 24`, API-1 `java.io` / `java.nio.channels`) implementation of
 * [com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl] wrapping a [ByteArray], [File],
 * [RandomAccessFile], [InputStream], [ReadableByteChannel], or lazy stream opener and executing
 * reads on [kotlinx.coroutines.Dispatchers.IO].
 */
open class JvmStreamFileReadableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onReadChunk: (suspend (Int) -> ByteArray?)? = null,
    onCloseCallback: (suspend () -> Unit)? = null
) : DefaultFileReadableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    byteOrder = byteOrder,
    onReadChunk = onReadChunk,
    onCloseCallback = onCloseCallback
) {
    open val nativeApi: String = "Java_IO_Stream_Channel_Dispatchers_IO"

    private var streamCloser: (() -> Unit)? = null

    constructor(
        file: File,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false }
    ) : this(
        holder = StreamReadHolder(
            isOnMainThread = isOnMainThread,
            sourceOpener = { ReadSource.Stream(FileInputStream(file)) }
        ),
        filePath = file.absolutePath,
        byteOrder = byteOrder
    )

    constructor(
        randomAccessFile: RandomAccessFile,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false }
    ) : this(
        holder = StreamReadHolder(
            isOnMainThread = isOnMainThread,
            sourceOpener = { ReadSource.Raf(randomAccessFile) }
        ),
        filePath = filePath,
        byteOrder = byteOrder
    )

    constructor(
        inputStream: InputStream,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false }
    ) : this(
        holder = StreamReadHolder(
            isOnMainThread = isOnMainThread,
            sourceOpener = { ReadSource.Stream(inputStream) }
        ),
        filePath = filePath,
        byteOrder = byteOrder
    )

    constructor(
        channel: ReadableByteChannel,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false }
    ) : this(
        holder = StreamReadHolder(
            isOnMainThread = isOnMainThread,
            sourceOpener = { ReadSource.Channel(channel) }
        ),
        filePath = filePath,
        byteOrder = byteOrder
    )

    constructor(
        streamOpener: () -> InputStream,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false }
    ) : this(
        holder = StreamReadHolder(
            isOnMainThread = isOnMainThread,
            sourceOpener = { ReadSource.Stream(streamOpener()) }
        ),
        filePath = filePath,
        byteOrder = byteOrder
    )

    private constructor(
        holder: StreamReadHolder,
        filePath: String?,
        byteOrder: ByteOrder
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        byteOrder = byteOrder,
        onReadChunk = { maxBytes -> holder.readChunk(maxBytes) },
        onCloseCallback = { holder.closeSource() }
    ) {
        this.streamCloser = { holder.closeSync() }
    }

    override fun dispose() {
        streamCloser?.invoke()
        streamCloser = null
        super.dispose()
    }

    private sealed class ReadSource : Closeable {
        abstract fun readBytes(limit: Int): ByteArray?

        class Stream(private val input: InputStream) : ReadSource() {
            override fun readBytes(limit: Int): ByteArray? {
                val buf = ByteArray(limit)
                val count = input.read(buf, 0, limit)
                if (count < 0) return null
                if (count == 0) return ByteArray(0)
                return if (count == limit) buf else buf.copyOfRange(0, count)
            }

            override fun close() {
                input.close()
            }
        }

        class Raf(private val raf: RandomAccessFile) : ReadSource() {
            override fun readBytes(limit: Int): ByteArray? {
                val buf = ByteArray(limit)
                val count = raf.read(buf, 0, limit)
                if (count < 0) return null
                if (count == 0) return ByteArray(0)
                return if (count == limit) buf else buf.copyOfRange(0, count)
            }

            override fun close() {
                raf.close()
            }
        }

        class Channel(private val channel: ReadableByteChannel) : ReadSource() {
            override fun readBytes(limit: Int): ByteArray? {
                val dst = NioByteBuffer.allocate(limit)
                val count = channel.read(dst)
                if (count < 0) return null
                if (count == 0) return ByteArray(0)
                val out = ByteArray(count)
                dst.flip()
                dst.get(out, 0, count)
                return out
            }

            override fun close() {
                channel.close()
            }
        }
    }

    private class StreamReadHolder(
        private val isOnMainThread: () -> Boolean,
        private val sourceOpener: () -> ReadSource
    ) {
        @Volatile
        private var activeSource: ReadSource? = null

        @Volatile
        private var closed: Boolean = false

        suspend fun readChunk(maxBytes: Int): ByteArray? = JvmIoExecutor.runOnIo(isOnMainThread) {
            synchronized(this) {
                if (closed) return@runOnIo null
                val src = activeSource ?: try {
                    sourceOpener().also { activeSource = it }
                } catch (_: Throwable) {
                    closed = true
                    return@runOnIo null
                }
                val limit = maxBytes.coerceIn(1, 1024 * 1024)
                val chunk = try {
                    src.readBytes(limit)
                } catch (_: Throwable) {
                    null
                }
                if (chunk == null) {
                    closed = true
                    runCatching { src.close() }
                    activeSource = null
                    return@runOnIo null
                }
                chunk
            }
        }

        suspend fun closeSource() {
            JvmIoExecutor.runOnIo(isOnMainThread) {
                closeSync()
            }
        }

        fun closeSync() {
            synchronized(this) {
                closed = true
                val src = activeSource
                activeSource = null
                if (src != null) {
                    runCatching { src.close() }
                }
            }
        }
    }
}
