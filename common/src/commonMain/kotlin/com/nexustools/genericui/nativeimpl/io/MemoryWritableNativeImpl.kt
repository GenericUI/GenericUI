package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.io.WritableByteBuffer

/**
 * In-memory [Writable] implementation backed by a [WritableByteBuffer], with optional
 * chunk, flush, commit, and close callbacks for streaming or buffered persistence.
 */
open class MemoryWritableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    override var highWaterMark: Int = 65536,
    override var byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    private val onWriteChunk: ((ByteArray, Int, Int) -> Int)? = null,
    private val onFlushCallback: (suspend (ByteArray) -> Unit)? = null,
    private val onCloseCallback: (suspend () -> Unit)? = null,
    private val onCommit: (suspend (ByteArray) -> Unit)? = null
) : Writable {
    protected val buffer = WritableByteBuffer(
        initialCapacity = maxOf(256, initialBytes.size),
        byteOrder = byteOrder
    )

    private var finishedState: Boolean = false
    private var disposedState: Boolean = false
    private var committed: Boolean = false
    private var closeCallbackInvoked: Boolean = false
    private var totalWritten: Long = 0L

    protected val writeListeners = mutableListOf<(ByteBuffer) -> Unit>()
    protected val drainListeners = mutableListOf<suspend () -> Unit>()
    protected val finishListeners = mutableListOf<suspend () -> Unit>()
    protected val errorListeners = mutableListOf<(Throwable) -> Unit>()
    protected val closeListeners = mutableListOf<() -> Unit>()

    init {
        if (initialBytes.isNotEmpty()) {
            buffer.writeBytes(initialBytes)
        }
    }

    override val isWritable: Boolean
        get() = !disposedState && !finishedState

    override val isFinished: Boolean
        get() = finishedState

    override val bytesWritten: Long
        get() = totalWritten

    override val isDisposed: Boolean
        get() = disposedState

    override fun write(buffer: ByteBuffer): Boolean {
        if (disposedState || finishedState) {
            val err = IllegalStateException("Cannot write to a closed or finished Writable stream")
            notifyErrorListeners(err)
            throw err
        }
        val bytes = buffer.remainingByteArray()
        buffer.position = buffer.limit
        return write(bytes, 0, bytes.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        if (disposedState || finishedState) {
            val err = IllegalStateException("Cannot write to a closed or finished Writable stream")
            notifyErrorListeners(err)
            throw err
        }
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid chunk slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return buffer.size < highWaterMark

        return try {
            onWriteChunk?.invoke(chunk, offset, length)
            buffer.byteOrder = byteOrder
            buffer.write(chunk, offset, length)
            totalWritten += length
            if (writeListeners.isNotEmpty()) {
                val slice = ByteBuffer.wrap(chunk.copyOfRange(offset, offset + length), byteOrder = byteOrder)
                writeListeners.toList().forEach { it(slice) }
            }
            buffer.size < highWaterMark
        } catch (t: Throwable) {
            notifyErrorListeners(t)
            throw t
        }
    }

    protected fun notifyErrorListeners(error: Throwable) {
        errorListeners.toList().forEach { it(error) }
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

    override fun onError(listener: (Throwable) -> Unit): Writable {
        errorListeners += listener
        return this
    }

    override fun onClose(listener: () -> Unit): Writable {
        closeListeners += listener
        return this
    }

    override suspend fun flush() {
        try {
            onFlushCallback?.invoke(buffer.toByteArray())
            drainListeners.toList().forEach { it() }
        } catch (t: Throwable) {
            notifyErrorListeners(t)
            throw t
        }
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        if (finishedState) return
        if (finalBuffer != null && finalBuffer.remaining > 0) {
            write(finalBuffer)
        }
        flush()
        if (!committed) {
            committed = true
            onCommit?.invoke(buffer.toByteArray())
        }
        finishedState = true
        finishListeners.toList().forEach { it() }
    }

    override suspend fun close() {
        if (!finishedState) {
            end()
        }
        if (!closeCallbackInvoked) {
            closeCallbackInvoked = true
            onCloseCallback?.invoke()
        }
        dispose()
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        finishedState = true
        closeListeners.toList().forEach { it() }
        writeListeners.clear()
        drainListeners.clear()
        finishListeners.clear()
        errorListeners.clear()
        closeListeners.clear()
    }

    override fun toByteArray(): ByteArray =
        buffer.toByteArray()

    override fun toByteBuffer(): ByteBuffer =
        ByteBuffer.wrap(buffer.toByteArray(), byteOrder = byteOrder)
}
