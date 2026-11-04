package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Default implementation of [HttpConnectionNativeImpl] delegating [Writable] operations to
 * [request] and [Readable] operations to [response].
 */
open class DefaultHttpConnectionNativeImpl(
    override val request: HttpRequest,
    override val response: HttpResponse
) : HttpConnectionNativeImpl {

    private var disposedState: Boolean = false

    override var byteOrder: ByteOrder
        get() = response.byteOrder
        set(value) {
            request.byteOrder = value
            response.byteOrder = value
        }

    override val isReadable: Boolean
        get() = response.isReadable

    override val isPaused: Boolean
        get() = response.isPaused

    override val isEnded: Boolean
        get() = response.isEnded

    override val bytesRead: Long
        get() = response.bytesRead

    override val isWritable: Boolean
        get() = request.isWritable

    override val isFinished: Boolean
        get() = request.isFinished

    override val bytesWritten: Long
        get() = request.bytesWritten

    override var highWaterMark: Int
        get() = request.highWaterMark
        set(value) {
            request.highWaterMark = value
        }

    override val isDisposed: Boolean
        get() = disposedState || (request.isDisposed && response.isDisposed)

    override suspend fun read(maxBytes: Int): ByteBuffer? =
        response.read(maxBytes)

    override suspend fun readAllBuffer(): ByteBuffer =
        response.readAllBuffer()

    override suspend fun readAllBytes(): ByteArray =
        response.readAllBytes()

    override suspend fun readAllText(encoding: String): String =
        response.readAllText(encoding)

    override fun onData(listener: suspend (ByteBuffer) -> Unit): Readable {
        response.onData(listener)
        return this
    }

    override fun onEnd(listener: suspend () -> Unit): Readable {
        response.onEnd(listener)
        return this
    }

    override fun pause(): Readable {
        response.pause()
        return this
    }

    override fun resume(): Readable {
        response.resume()
        return this
    }

    override fun unshift(buffer: ByteBuffer) {
        response.unshift(buffer)
    }

    override fun write(buffer: ByteBuffer): Boolean =
        request.write(buffer)

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean =
        request.write(chunk, offset, length)

    override fun onWrite(listener: (ByteBuffer) -> Unit): Writable {
        request.onWrite(listener)
        return this
    }

    override fun onDrain(listener: suspend () -> Unit): Writable {
        request.onDrain(listener)
        return this
    }

    override fun onFinish(listener: suspend () -> Unit): Writable {
        request.onFinish(listener)
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Stream {
        request.onError(listener)
        response.onError(listener)
        return this
    }

    override fun onClose(listener: () -> Unit): Stream {
        response.onClose(listener)
        return this
    }

    override suspend fun flush() {
        request.flush()
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        request.end(finalBuffer)
    }

    override suspend fun close() {
        try {
            request.close()
        } finally {
            response.close()
            disposedState = true
        }
    }

    override fun dispose() {
        if (disposedState) return
        disposedState = true
        request.dispose()
        response.dispose()
    }

    override fun toByteArray(): ByteArray =
        response.bodyBytes

    override fun toByteBuffer(): ByteBuffer =
        response.bodyBuffer
}
