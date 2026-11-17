@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.fileHandleForReadingAtPath
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import kotlin.coroutines.resume

class AppleFileReadableNativeImpl(
    override val filePath: String,
    private val securityScopedUrl: NSURL? = null,
    initialByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
) : FileReadableNativeImpl {
    private val state = AppleStreamState(initialByteOrder = initialByteOrder)
    private val ioQueue = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)

    private var fileHandle: NSFileHandle? = null
    private var openAttempted = false
    private var securityAccessActive = false

    override val isReadable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isEnded }

    override val isPaused: Boolean
        get() = state.lock.withLock { state.isPaused }

    override val isEnded: Boolean
        get() = state.lock.withLock { state.isEnded }

    override val bytesRead: Long
        get() = state.lock.withLock { state.bytesRead }

    override var byteOrder: ByteOrder
        get() = state.lock.withLock { state.byteOrder }
        set(value) {
            state.lock.withLock { state.byteOrder = value }
        }

    override val isDisposed: Boolean
        get() = state.lock.withLock { state.isDisposed }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (maxBytes <= 0 || isPaused || isDisposed || isEnded) return null
        state.pollPushback(maxBytes)?.let { return it }

        val boundedMax = maxBytes.coerceIn(1, 1024 * 1024)
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(ioQueue) {
                val chunkResult = readNextChunkSync(boundedMax)
                continuation.resume(chunkResult)
            }
        }
    }

    private fun readNextChunkSync(maxBytes: Int): ByteBuffer? {
        val handle = ensureHandleOpened() ?: run {
            state.lock.withLock { state.isEnded = true }
            return null
        }

        val dataResult = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val data: NSData? = handle.readDataUpToLength(maxBytes.toULong(), error = err.ptr)
            val nsErr = err.value
            data to nsErr
        }

        val nsError = dataResult.second
        if (nsError != null) {
            state.emitError(IllegalStateException("File read error ($filePath): ${nsError.localizedDescription}"))
            closeHandleOnly()
            state.lock.withLock { state.isEnded = true }
            return null
        }

        val bytes = AppleDataBridge.nsDataToByteArray(dataResult.first)
        if (bytes.isEmpty()) {
            closeHandleOnly()
            state.lock.withLock { state.isEnded = true }
            return null
        }

        state.recordRead(bytes.size)
        return ByteBuffer.wrap(bytes, byteOrder = byteOrder)
    }

    override fun onData(listener: suspend (ByteBuffer) -> Unit): Readable {
        state.addDataListener(listener)
        return this
    }

    override fun onEnd(listener: suspend () -> Unit): Readable {
        state.addEndListener(listener)
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Readable {
        state.addErrorListener(listener)
        return this
    }

    override fun onClose(listener: () -> Unit): Readable {
        state.addCloseListener(listener)
        return this
    }

    override fun pause(): Readable {
        state.pause()
        return this
    }

    override fun resume(): Readable {
        state.resume()
        return this
    }

    override fun unshift(buffer: ByteBuffer) {
        state.unshift(buffer)
    }

    override suspend fun close() {
        closeHandleOnly()
        state.emitEnd()
        state.markDisposedAndNotifyClose()
    }

    override fun dispose() {
        closeHandleOnly()
        state.markDisposedAndNotifyClose()
    }

    private fun ensureHandleOpened(): NSFileHandle? {
        return state.lock.withLock {
            if (state.isDisposed) return null
            if (openAttempted) return fileHandle
            openAttempted = true
            if (securityScopedUrl != null) {
                securityAccessActive = securityScopedUrl.startAccessingSecurityScopedResource()
            }
            val handle = NSFileHandle.fileHandleForReadingAtPath(filePath)
            fileHandle = handle
            if (handle == null) {
                releaseSecurityScopeLocked()
                state.emitError(IllegalStateException("Failed to open file for reading: $filePath"))
            }
            handle
        }
    }

    private fun closeHandleOnly() {
        val handle = state.lock.withLock {
            val h = fileHandle
            fileHandle = null
            releaseSecurityScopeLocked()
            h
        }
        if (handle != null) {
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                handle.closeAndReturnError(err.ptr)
            }
        }
    }

    private fun releaseSecurityScopeLocked() {
        if (securityAccessActive && securityScopedUrl != null) {
            securityAccessActive = false
            securityScopedUrl.stopAccessingSecurityScopedResource()
        }
    }
}
