package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.MemoryReadableNativeImpl

/**
 * Default implementation of [HttpResponseNativeImpl] backed by [MemoryReadableNativeImpl].
 */
open class DefaultHttpResponseNativeImpl(
    override val statusCode: Int,
    override val statusText: String = "",
    override val headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray = ByteArray(0),
    override val url: String = "",
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : MemoryReadableNativeImpl(initialBodyBytes, byteOrder), HttpResponseNativeImpl {

    override val bodyBytes: ByteArray = initialBodyBytes.copyOf()

    override val bodyBuffer: ByteBuffer
        get() = ByteBuffer.wrap(bodyBytes, byteOrder = byteOrder)

    override val bodyText: String
        get() = bodyBytes.decodeToString()
}
