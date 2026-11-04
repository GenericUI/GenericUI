package com.nexustools.genericui.net.http

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl

/**
 * 100% asynchronous, non-blocking cross-platform HTTP/HTTPS client (`fetch` / `axios` style).
 *
 * Returns [HttpConnection] duplex [com.nexustools.genericui.io.Stream] handles (`Readable` + `Writable`)
 * where `connection.request` is [com.nexustools.genericui.io.Writable] and `connection.response` is
 * [com.nexustools.genericui.io.Readable].
 */
class HttpClient internal constructor(
    internal val nativeImpl: HttpClientNativeImpl,
    val defaultHeaders: Map<String, String>,
    val defaultTimeoutMs: Long,
    val followRedirects: Boolean
) : Disposable {

    constructor(
        defaultHeaders: Map<String, String> = emptyMap(),
        defaultTimeoutMs: Long = 30_000L,
        followRedirects: Boolean = true
    ) : this(
        nativeImpl = GenericUI.activeFactory?.createHttpClient() ?: DefaultHttpClientNativeImpl(),
        defaultHeaders = defaultHeaders,
        defaultTimeoutMs = defaultTimeoutMs,
        followRedirects = followRedirects
    )

    override val isDisposed: Boolean
        get() = nativeImpl.isDisposed

    suspend fun request(request: HttpRequest): HttpConnection =
        nativeImpl.openConnection(withDefaults(request))

    suspend fun request(
        method: HttpMethod,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): HttpConnection = request(
        HttpRequest(
            url = url,
            method = method,
            headers = headers,
            bodyBytes = body,
            timeoutMs = defaultTimeoutMs,
            followRedirects = followRedirects
        )
    )

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
        request(HttpMethod.GET, url, headers)

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        get(url, headers).bodyText

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        get(url, headers).bodyBytes

    suspend fun getBuffer(url: String, headers: Map<String, String> = emptyMap()): ByteBuffer =
        get(url, headers).bodyBuffer

    suspend fun post(
        url: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = request(
        method = HttpMethod.POST,
        url = url,
        headers = withContentType(headers, contentType),
        body = body
    )

    suspend fun postBuffer(
        url: String,
        body: ByteBuffer,
        contentType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = post(url, body.toByteArray(), contentType, headers)

    suspend fun postText(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = post(url, body.encodeToByteArray(), contentType, headers)

    suspend fun put(
        url: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = request(
        method = HttpMethod.PUT,
        url = url,
        headers = withContentType(headers, contentType),
        body = body
    )

    suspend fun putText(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = put(url, body.encodeToByteArray(), contentType, headers)

    suspend fun patch(
        url: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): HttpConnection = request(
        method = HttpMethod.PATCH,
        url = url,
        headers = withContentType(headers, contentType),
        body = body
    )

    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
        request(HttpMethod.DELETE, url, headers)

    suspend fun head(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
        request(HttpMethod.HEAD, url, headers)

    suspend fun options(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
        request(HttpMethod.OPTIONS, url, headers)

    fun registerRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {
        nativeImpl.registerRoute(urlPattern, handler)
    }

    fun unregisterRoute(urlPattern: String) {
        nativeImpl.unregisterRoute(urlPattern)
    }

    fun clearRoutes() {
        nativeImpl.clearRoutes()
    }

    override fun dispose() {
        nativeImpl.dispose()
    }

    private fun withDefaults(req: HttpRequest): HttpRequest {
        if (defaultHeaders.isNotEmpty()) {
            val merged = LinkedHashMap<String, String>()
            merged.putAll(defaultHeaders)
            merged.putAll(req.headers)
            req.headers = merged
        }
        return req
    }

    private fun withContentType(headers: Map<String, String>, contentType: String): Map<String, String> {
        if (headers.keys.any { it.equals("Content-Type", ignoreCase = true) }) {
            return headers
        }
        val merged = LinkedHashMap<String, String>(headers.size + 1)
        merged["Content-Type"] = contentType
        merged.putAll(headers)
        return merged
    }

    companion object {
        suspend fun request(request: HttpRequest): HttpConnection =
            HttpClient().useOnce { it.request(request) }

        suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
            HttpClient().useOnce { it.get(url, headers) }

        suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
            HttpClient().useOnce { it.getText(url, headers) }

        suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
            HttpClient().useOnce { it.getBytes(url, headers) }

        suspend fun getBuffer(url: String, headers: Map<String, String> = emptyMap()): ByteBuffer =
            HttpClient().useOnce { it.getBuffer(url, headers) }

        suspend fun post(
            url: String,
            body: ByteArray? = null,
            contentType: String = "application/json",
            headers: Map<String, String> = emptyMap()
        ): HttpConnection = HttpClient().useOnce { it.post(url, body, contentType, headers) }

        suspend fun postBuffer(
            url: String,
            body: ByteBuffer,
            contentType: String = "application/octet-stream",
            headers: Map<String, String> = emptyMap()
        ): HttpConnection = HttpClient().useOnce { it.postBuffer(url, body, contentType, headers) }

        suspend fun postText(
            url: String,
            body: String,
            contentType: String = "application/json",
            headers: Map<String, String> = emptyMap()
        ): HttpConnection = HttpClient().useOnce { it.postText(url, body, contentType, headers) }

        suspend fun put(
            url: String,
            body: ByteArray? = null,
            contentType: String = "application/json",
            headers: Map<String, String> = emptyMap()
        ): HttpConnection = HttpClient().useOnce { it.put(url, body, contentType, headers) }

        suspend fun patch(
            url: String,
            body: ByteArray? = null,
            contentType: String = "application/json",
            headers: Map<String, String> = emptyMap()
        ): HttpConnection = HttpClient().useOnce { it.patch(url, body, contentType, headers) }

        suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): HttpConnection =
            HttpClient().useOnce { it.delete(url, headers) }

        fun registerRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {
            DefaultHttpClientNativeImpl.registerStaticRoute(urlPattern, handler)
        }

        fun unregisterRoute(urlPattern: String) {
            DefaultHttpClientNativeImpl.unregisterStaticRoute(urlPattern)
        }

        fun clearRoutes() {
            DefaultHttpClientNativeImpl.clearStaticRoutes()
        }

        private suspend inline fun <T> HttpClient.useOnce(block: (HttpClient) -> T): T =
            try {
                block(this)
            } finally {
                dispose()
            }
    }
}
