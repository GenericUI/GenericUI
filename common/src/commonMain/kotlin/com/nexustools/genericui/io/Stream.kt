package com.nexustools.genericui.io

/**
 * Node.js-inspired duplex stream interface combining [Readable] and [Writable].
 */
interface Stream : Readable, Writable {
    override var byteOrder: ByteOrder
    override fun onError(listener: (Throwable) -> Unit): Stream
    override fun onClose(listener: () -> Unit): Stream
    override suspend fun close()
}
