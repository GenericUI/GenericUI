package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileWritableNativeImpl
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.Flushable
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer as NioByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Shared JVM and Android (`minSdk >= 24`, API-1 `java.io` / `java.nio.channels`) implementation of
 * [com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl] wrapping a [File], [RandomAccessFile],
 * [OutputStream], [WritableByteChannel], lazy stream opener, or in-memory buffer and executing
 * flushes and closes on [kotlinx.coroutines.Dispatchers.IO].
 */
open class JvmStreamFileWritableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    appendMode: Boolean = false,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onWriteChunk: ((ByteArray, Int, Int) -> Int)? = null,
    onFlushCallback: (suspend (ByteArray) -> Unit)? = null,
    onCloseCallback: (suspend () -> Unit)? = null,
    onCommit: (suspend (ByteArray) -> Unit)? = onFlushCallback
) : DefaultFileWritableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    appendMode = appendMode,
    byteOrder = byteOrder,
    onWriteChunk = onWriteChunk,
    onFlushCallback = onFlushCallback,
    onCloseCallback = onCloseCallback,
    onCommit = onCommit
) {
    open val nativeApi: String = "Java_IO_Stream_Channel_Dispatchers_IO"

    var flushCount: Int = 0
        protected set

    private var streamCloser: (() -> Unit)? = null

    constructor(
        outputStream: OutputStream,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false },
        onCommit: (suspend () -> Unit)? = null
    ) : this(
        holder = StreamWriteHolder(
            isOnMainThread = isOnMainThread,
            sinkOpener = { WriteSink.Stream(outputStream) },
            onCommit = onCommit
        ),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder
    )

    constructor(
        channel: WritableByteChannel,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false },
        onCommit: (suspend () -> Unit)? = null
    ) : this(
        holder = StreamWriteHolder(
            isOnMainThread = isOnMainThread,
            sinkOpener = { WriteSink.Channel(channel) },
            onCommit = onCommit
        ),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder
    )

    constructor(
        file: File,
        append: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false },
        onCommit: (suspend () -> Unit)? = null
    ) : this(
        holder = StreamWriteHolder(
            isOnMainThread = isOnMainThread,
            sinkOpener = {
                file.parentFile?.mkdirs()
                WriteSink.Stream(FileOutputStream(file, append))
            },
            onCommit = onCommit
        ),
        filePath = file.absolutePath,
        appendMode = append,
        byteOrder = byteOrder
    )

    constructor(
        randomAccessFile: RandomAccessFile,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false },
        onCommit: (suspend () -> Unit)? = null
    ) : this(
        holder = StreamWriteHolder(
            isOnMainThread = isOnMainThread,
            sinkOpener = {
                if (appendMode) {
                    randomAccessFile.seek(randomAccessFile.length())
                }
                WriteSink.Raf(randomAccessFile)
            },
            onCommit = onCommit
        ),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder
    )

    constructor(
        streamOpener: () -> OutputStream,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        isOnMainThread: () -> Boolean = { false },
        onCommit: (suspend () -> Unit)? = null
    ) : this(
        holder = StreamWriteHolder(
            isOnMainThread = isOnMainThread,
            sinkOpener = { WriteSink.Stream(streamOpener()) },
            onCommit = onCommit
        ),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder
    )

    private constructor(
        holder: StreamWriteHolder,
        filePath: String?,
        appendMode: Boolean,
        byteOrder: ByteOrder
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder,
        onWriteChunk = { chunk, offset, length -> holder.writeChunk(chunk, offset, length) },
        onFlushCallback = { holder.flushSink() },
        onCloseCallback = { holder.closeSink() },
        onCommit = { holder.commitSink() }
    ) {
        this.streamCloser = { holder.closeSync() }
    }

    override suspend fun flush() {
        if (!isDisposed) {
            flushCount++
        }
        super.flush()
    }

    override fun dispose() {
        streamCloser?.invoke()
        streamCloser = null
        super.dispose()
    }

    private sealed class WriteSink : Closeable {
        abstract fun writeBytes(chunk: ByteArray, offset: Int, length: Int): Int
        abstract fun flushSink()

        class Stream(private val output: OutputStream) : WriteSink() {
            override fun writeBytes(chunk: ByteArray, offset: Int, length: Int): Int {
                output.write(chunk, offset, length)
                return length
            }

            override fun flushSink() {
                output.flush()
            }

            override fun close() {
                runCatching { output.flush() }
                output.close()
            }
        }

        class Raf(private val raf: RandomAccessFile) : WriteSink() {
            override fun writeBytes(chunk: ByteArray, offset: Int, length: Int): Int {
                raf.write(chunk, offset, length)
                return length
            }

            override fun flushSink() {
                runCatching { raf.fd.sync() }
            }

            override fun close() {
                runCatching { raf.fd.sync() }
                raf.close()
            }
        }

        class Channel(private val channel: WritableByteChannel) : WriteSink() {
            override fun writeBytes(chunk: ByteArray, offset: Int, length: Int): Int {
                val src = NioByteBuffer.wrap(chunk, offset, length)
                var total = 0
                while (src.hasRemaining()) {
                    val n = channel.write(src)
                    if (n <= 0) break
                    total += n
                }
                return total
            }

            override fun flushSink() {
                (channel as? Flushable)?.flush()
            }

            override fun close() {
                runCatching { (channel as? Flushable)?.flush() }
                channel.close()
            }
        }
    }

    private class StreamWriteHolder(
        private val isOnMainThread: () -> Boolean,
        private val sinkOpener: () -> WriteSink,
        private val onCommit: (suspend () -> Unit)?
    ) {
        @Volatile
        private var activeSink: WriteSink? = null

        @Volatile
        private var closed: Boolean = false

        fun writeChunk(chunk: ByteArray, offset: Int, length: Int): Int {
            synchronized(this) {
                if (closed) return 0
                val sink = activeSink ?: try {
                    sinkOpener().also { activeSink = it }
                } catch (_: Throwable) {
                    return 0
                }
                return try {
                    sink.writeBytes(chunk, offset, length)
                } catch (_: Throwable) {
                    0
                }
            }
        }

        suspend fun flushSink() {
            JvmIoExecutor.runOnIo(isOnMainThread) {
                synchronized(this) {
                    if (!closed) {
                        runCatching { activeSink?.flushSink() }
                    }
                }
            }
        }

        suspend fun commitSink() {
            flushSink()
            onCommit?.invoke()
        }

        suspend fun closeSink() {
            JvmIoExecutor.runOnIo(isOnMainThread) {
                closeSync()
            }
        }

        fun closeSync() {
            synchronized(this) {
                closed = true
                val sink = activeSink
                activeSink = null
                if (sink != null) {
                    runCatching { sink.close() }
                }
            }
        }
    }
}
