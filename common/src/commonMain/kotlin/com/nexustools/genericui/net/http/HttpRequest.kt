package com.nexustools.genericui.net.http

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.net.DefaultHttpRequestNativeImpl

/**
 * Node.js `http.ClientRequest`-inspired [Writable] HTTP request stream.
 */
interface HttpRequest : Writable {
    val url: String
    val method: HttpMethod
    var headers: Map<String, String>
    var timeoutMs: Long
    var followRedirects: Boolean

    val bodyBytes: ByteArray?
    val bodyBuffer: ByteBuffer?
    val bodyText: String?

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun setHeader(name: String, value: String): HttpRequest

    companion object {
        operator fun invoke(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            headers: Map<String, String> = emptyMap(),
            bodyBytes: ByteArray? = null,
            timeoutMs: Long = 30_000L,
            followRedirects: Boolean = true
        ): HttpRequest = DefaultHttpRequestNativeImpl(
            url = url,
            method = method,
            headers = headers,
            initialBodyBytes = bodyBytes,
            timeoutMs = timeoutMs,
            followRedirects = followRedirects
        )
    }
}
