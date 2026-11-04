package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder

/**
 * Default implementation of [FileReadableNativeImpl] supporting both in-memory bytes and
 * chunk-reading callbacks.
 */
open class DefaultFileReadableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    override val filePath: String? = null,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    private val onReadChunk: (suspend (Int) -> ByteArray?)? = null,
    private val onCloseCallback: (suspend () -> Unit)? = null
) : MemoryReadableNativeImpl(initialBytes, byteOrder), FileReadableNativeImpl {

    private var closeCallbackInvoked: Boolean = false

    override suspend fun readChunkFromSource(maxBytes: Int): ByteBuffer? {
        val buffered = super.readChunkFromSource(maxBytes)
        if (buffered != null && buffered.remaining > 0) {
            return buffered
        }
        val chunkBytes = onReadChunk?.invoke(maxBytes) ?: return null
        if (chunkBytes.isEmpty()) return null
        return ByteBuffer.wrap(chunkBytes, byteOrder = byteOrder)
    }

    override suspend fun close() {
        if (!closeCallbackInvoked) {
            closeCallbackInvoked = true
            onCloseCallback?.invoke()
        }
        super.close()
    }
}
