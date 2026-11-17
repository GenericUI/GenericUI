@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeData
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import kotlin.coroutines.resume

class AppleFileWritableNativeImpl(
    override val filePath: String,
    override val appendMode: Boolean = false,
    private val securityScopedUrl: NSURL? = null,
    initialHighWaterMark: Int = 64 * 1024,
    initialByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
) : FileWritableNativeImpl {
    private val state = AppleStreamState(
        initialByteOrder = initialByteOrder,
        initialHighWaterMark = initialHighWaterMark
    )
    private val ioQueue = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)

    private var fileHandle: NSFileHandle? = null
    private var openAttempted = false
    private var openFailed = false
    private var securityAccessActive = false

    override val isWritable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isFinished && !openFailed }

    override val isFinished: Boolean
        get() = state.lock.withLock { state.isFinished }

    override val bytesWritten: Long
        get() = state.lock.withLock { state.bytesWritten }

    override var highWaterMark: Int
        get() = state.lock.withLock { state.highWaterMark }
        set(value) {
            state.lock.withLock { state.highWaterMark = value }
        }

    override var byteOrder: ByteOrder
        get() = state.lock.withLock { state.byteOrder }
        set(value) {
            state.lock.withLock { state.byteOrder = value }
        }

    override val isDisposed: Boolean
        get() = state.lock.withLock { state.isDisposed }

    override fun write(buffer: ByteBuffer): Boolean {
        val slice = buffer.remainingByteArray()
        return write(slice, 0, slice.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice offset=$offset length=$length size=${chunk.size}"
        }
        if (!isWritable) return false
        val slice = if (offset == 0 && length == chunk.size) chunk.copyOf() else chunk.copyOfRange(offset, offset + length)
        val handle = ensureHandleOpened() ?: return false
        if (slice.isEmpty()) return true

        val nsData = AppleDataBridge.byteArrayToNSData(slice)
        val writeErr = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val ok = handle.writeData(nsData, error = err.ptr)
            if (ok) null else (err.value?.localizedDescription ?: "Failed to write file chunk")
        }
        if (writeErr != null) {
            state.emitError(IllegalStateException("File write error ($filePath): $writeErr"))
            return false
        }

        state.recordWrite(slice.size)
        state.emitWrite(ByteBuffer.wrap(slice, byteOrder = byteOrder))
        return true
    }

    override fun onWrite(listener: (ByteBuffer) -> Unit): Writable {
        state.addWriteListener(listener)
        return this
    }

    override fun onDrain(listener: suspend () -> Unit): Writable {
        state.addDrainListener(listener)
        return this
    }

    override fun onFinish(listener: suspend () -> Unit): Writable {
        state.addFinishListener(listener)
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Writable {
        state.addErrorListener(listener)
        return this
    }

    override fun onClose(listener: () -> Unit): Writable {
        state.addCloseListener(listener)
        return this
    }

    override suspend fun flush() {
        val handle = ensureHandleOpened() ?: return
        val syncErr = suspendCancellableCoroutine<String?> { continuation ->
            dispatch_async(ioQueue) {
                val errMsg = memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    if (handle.synchronizeAndReturnError(err.ptr)) null
                    else (err.value?.localizedDescription ?: "synchronize failed")
                }
                continuation.resume(errMsg)
            }
        }
        if (syncErr != null) {
            state.emitError(IllegalStateException("File flush error ($filePath): $syncErr"))
        } else {
            state.emitDrain()
        }
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        if (finalBuffer != null && finalBuffer.hasRemaining) {
            write(finalBuffer)
        }
        ensureHandleOpened()
        flush()
        closeHandleOnly()
        state.emitFinish()
    }

    override suspend fun close() {
        flush()
        closeHandleOnly()
        state.emitFinish()
        state.markDisposedAndNotifyClose()
    }

    override fun dispose() {
        closeHandleOnly()
        state.markDisposedAndNotifyClose()
    }

    override fun toByteArray(): ByteArray {
        val data = NSData.create(contentsOfFile = filePath)
        return AppleDataBridge.nsDataToByteArray(data)
    }

    override fun toByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap(toByteArray(), byteOrder = byteOrder)
    }

    private fun ensureHandleOpened(): NSFileHandle? {
        return state.lock.withLock {
            if (state.isDisposed || state.isFinished) return null
            if (openAttempted) return fileHandle
            openAttempted = true

            if (securityScopedUrl != null) {
                securityAccessActive = securityScopedUrl.startAccessingSecurityScopedResource()
            }

            val fm = NSFileManager.defaultManager
            val parentDir = NSString.create(string = filePath).stringByDeletingLastPathComponent
            if (parentDir.isNotEmpty() && !fm.fileExistsAtPath(parentDir)) {
                memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    fm.createDirectoryAtPath(parentDir, withIntermediateDirectories = true, attributes = null, error = err.ptr)
                }
            }

            if (!fm.fileExistsAtPath(filePath)) {
                fm.createFileAtPath(filePath, contents = null, attributes = null)
            }

            val handle = NSFileHandle.fileHandleForWritingAtPath(filePath)
            if (handle == null) {
                openFailed = true
                releaseSecurityScopeLocked()
                state.emitError(IllegalStateException("Failed to open file for writing: $filePath"))
                return null
            }

            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                if (appendMode) {
                    val endOffset = alloc<kotlinx.cinterop.ULongVar>()
                    handle.seekToEndReturningOffset(endOffset.ptr, error = err.ptr)
                } else {
                    handle.truncateAtOffset(0uL, error = err.ptr)
                }
            }

            fileHandle = handle
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
                handle.synchronizeAndReturnError(err.ptr)
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
