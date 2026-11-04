package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.MemoryWritableNativeImpl
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest

/**
 * Default implementation of [HttpRequestNativeImpl] backed by [MemoryWritableNativeImpl].
 */
open class DefaultHttpRequestNativeImpl(
    override val url: String,
    override val method: HttpMethod = HttpMethod.GET,
    headers: Map<String, String> = emptyMap(),
    private val initialBodyBytes: ByteArray? = null,
    override var timeoutMs: Long = 30_000L,
    override var followRedirects: Boolean = true,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : MemoryWritableNativeImpl(
    initialBytes = initialBodyBytes ?: ByteArray(0),
    byteOrder = byteOrder
), HttpRequestNativeImpl {

    private var headerMap: LinkedHashMap<String, String> = LinkedHashMap<String, String>().apply {
        putAll(headers)
    }

    override var headers: Map<String, String>
        get() = headerMap
        set(value) {
            headerMap = LinkedHashMap<String, String>().apply { putAll(value) }
        }

    override val bodyBytes: ByteArray?
        get() {
            val written = toByteArray()
            return if (written.isNotEmpty() || initialBodyBytes != null) written else null
        }

    override val bodyBuffer: ByteBuffer?
        get() = bodyBytes?.let { ByteBuffer.wrap(it, byteOrder = byteOrder) }

    override val bodyText: String?
        get() = bodyBytes?.decodeToString()

    override fun setHeader(name: String, value: String): HttpRequest {
        val existingKey = headerMap.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (existingKey != null && existingKey != name) {
            headerMap.remove(existingKey)
        }
        headerMap[name] = value
        return this
    }
}
