package com.nexustools.genericui.net.http

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.nativeimpl.net.DefaultHttpResponseNativeImpl

/**
 * Node.js `http.IncomingMessage`-inspired [Readable] HTTP response stream.
 */
interface HttpResponse : Readable {
    val statusCode: Int
    val statusText: String
    val headers: Map<String, String>
    val url: String

    val isSuccessful: Boolean
        get() = statusCode in 200..299

    val bodyBytes: ByteArray
    val bodyBuffer: ByteBuffer
    val bodyText: String

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    companion object {
        operator fun invoke(
            statusCode: Int,
            statusText: String = "",
            headers: Map<String, String> = emptyMap(),
            bodyBytes: ByteArray = ByteArray(0),
            url: String = ""
        ): HttpResponse = DefaultHttpResponseNativeImpl(
            statusCode = statusCode,
            statusText = statusText,
            headers = headers,
            initialBodyBytes = bodyBytes,
            url = url
        )
    }
}
