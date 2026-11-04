package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.ReadableByteBuffer

/**
 * In-memory [com.nexustools.genericui.io.Readable] implementation backed by a [ByteBuffer].
 */
open class MemoryReadableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : ReadableByteBuffer(initialBytes, byteOrder) {

    constructor(
        buffer: ByteBuffer,
        byteOrder: ByteOrder = buffer.byteOrder
    ) : this(buffer.remainingByteArray(), byteOrder)
}
