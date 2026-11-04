package com.nexustools.genericui.net.http

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Stream

/**
 * Duplex [Stream] (`Readable` + `Writable`) representing an active HTTP request/response exchange.
 *
 * Delegates [com.nexustools.genericui.io.Writable] operations to [request] and
 * [com.nexustools.genericui.io.Readable] operations to [response].
 */
interface HttpConnection : Stream {
    val request: HttpRequest
    val response: HttpResponse

    val url: String
        get() = response.url.ifEmpty { request.url }

    val method: HttpMethod
        get() = request.method

    val statusCode: Int
        get() = response.statusCode

    val statusText: String
        get() = response.statusText

    val headers: Map<String, String>
        get() = response.headers

    val isSuccessful: Boolean
        get() = response.isSuccessful

    val bodyBytes: ByteArray
        get() = response.bodyBytes

    val bodyBuffer: ByteBuffer
        get() = response.bodyBuffer

    val bodyText: String
        get() = response.bodyText

    fun header(name: String): String? =
        response.header(name)
}
