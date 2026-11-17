package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl] backed by
 * `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` on
 * [kotlinx.coroutines.Dispatchers.IO], consulting [DefaultHttpClientNativeImpl] mock routes first
 * when registered in unit tests.
 */
open class JvmHttpUrlConnectionClient(
    private val defaultHandler: (suspend (HttpRequest) -> HttpResponse)? = null,
    private val isOnMainThread: () -> Boolean = { false },
    private val onRequestError: ((HttpRequest, Throwable) -> Unit)? = null,
    private val returnZeroStatusOnFailure: () -> Boolean = { false }
) : DefaultHttpClientNativeImpl() {

    open val nativeApi: String = "java_net_HttpURLConnection"

    private val capturedRequests = ArrayList<HttpRequest>()

    var requestCount: Int = 0
        protected set

    fun requestHistory(): List<HttpRequest> =
        synchronized(capturedRequests) { capturedRequests.toList() }

    protected open fun createRequest(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        initialBodyBytes: ByteArray?,
        timeoutMs: Long,
        followRedirects: Boolean,
        byteOrder: ByteOrder
    ): JvmHttpRequestNativeImpl = JvmHttpRequestNativeImpl(
        url = url,
        method = method,
        headers = headers,
        initialBodyBytes = initialBodyBytes,
        timeoutMs = timeoutMs,
        followRedirects = followRedirects,
        byteOrder = byteOrder
    )

    protected open fun createResponse(
        statusCode: Int,
        statusText: String = "",
        headers: Map<String, String> = emptyMap(),
        initialBodyBytes: ByteArray = ByteArray(0),
        url: String = "",
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ): JvmHttpResponseNativeImpl = JvmHttpResponseNativeImpl(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers,
        initialBodyBytes = initialBodyBytes,
        url = url,
        byteOrder = byteOrder
    )

    protected open fun createConnection(
        request: HttpRequest,
        response: HttpResponse
    ): JvmHttpConnectionNativeImpl = JvmHttpConnectionNativeImpl(request, response)

    protected open fun configureHttpsConnection(conn: HttpsURLConnection, request: HttpRequest) {}

    protected open fun configureConnection(conn: HttpURLConnection, request: HttpRequest) {}

    protected open fun wrapRequest(request: HttpRequest): JvmHttpRequestNativeImpl =
        request as? JvmHttpRequestNativeImpl ?: createRequest(
            url = request.url,
            method = request.method,
            headers = request.headers,
            initialBodyBytes = request.bodyBytes,
            timeoutMs = request.timeoutMs,
            followRedirects = request.followRedirects,
            byteOrder = request.byteOrder
        )

    protected open fun wrapResponse(raw: HttpResponse, request: HttpRequest): JvmHttpResponseNativeImpl {
        if (raw is JvmHttpResponseNativeImpl) return raw
        return createResponse(
            statusCode = raw.statusCode,
            statusText = raw.statusText,
            headers = raw.headers,
            initialBodyBytes = raw.bodyBytes,
            url = raw.url.ifEmpty { request.url },
            byteOrder = raw.byteOrder
        )
    }

    override suspend fun execute(request: HttpRequest): JvmHttpResponseNativeImpl {
        synchronized(capturedRequests) {
            requestCount += 1
            capturedRequests.add(request)
        }
        if (isDisposed) {
            return createResponse(
                statusCode = 499,
                statusText = "Client Closed Request",
                url = request.url,
                byteOrder = request.byteOrder
            )
        }

        val matchedRoute = findRouteHandler(request.url) ?: defaultHandler
        if (matchedRoute != null || isMockOrOfflineUrl(request.url)) {
            val raw = matchedRoute?.invoke(request) ?: super.execute(request)
            return wrapResponse(raw, request)
        }

        return JvmIoExecutor.runOnIoSuspend(isOnMainThread) {
            try {
                val conn = openConfiguredConnection(request)
                try {
                    val statusCode = conn.responseCode
                    val statusText = conn.responseMessage?.takeIf { it.isNotEmpty() }
                        ?: statusTextForCode(statusCode)
                    val headers = extractHeaders(conn)
                    val bodyStream = if (statusCode >= 400) {
                        conn.errorStream ?: runCatching { conn.inputStream }.getOrNull()
                    } else {
                        runCatching { conn.inputStream }.getOrNull()
                    }
                    val bodyBytes = bodyStream?.use { it.readBytes() } ?: ByteArray(0)
                    val resolvedUrl = conn.url?.toString() ?: request.url
                    createResponse(
                        statusCode = statusCode,
                        statusText = statusText,
                        headers = headers,
                        initialBodyBytes = bodyBytes,
                        url = resolvedUrl,
                        byteOrder = request.byteOrder
                    )
                } finally {
                    runCatching { conn.disconnect() }
                }
            } catch (e: Throwable) {
                onRequestError?.invoke(request, e)
                val reason = "${e::class.simpleName ?: "NetworkError"}: ${e.message ?: "Connection failed"}"
                val shouldReturnZero = returnZeroStatusOnFailure() ||
                    request.headers.containsKey("X-Episode-Caster-Client-Id") ||
                    request.url.contains("/v1/")
                if (shouldReturnZero) {
                    createResponse(
                        statusCode = 0,
                        statusText = reason,
                        headers = emptyMap(),
                        initialBodyBytes = ByteArray(0),
                        url = request.url,
                        byteOrder = request.byteOrder
                    )
                } else {
                    wrapResponse(super.execute(request), request)
                }
            }
        }
    }

    override suspend fun openConnection(request: HttpRequest): JvmHttpConnectionNativeImpl {
        val nativeRequest = wrapRequest(request)
        val response = execute(nativeRequest)
        return createConnection(nativeRequest, response)
    }

    override fun dispose() {
        synchronized(capturedRequests) {
            capturedRequests.clear()
        }
        super.dispose()
    }

    private fun openConfiguredConnection(request: HttpRequest): HttpURLConnection {
        val url = URI.create(request.url.trim()).toURL()
        val conn = url.openConnection() as HttpURLConnection
        val timeout = request.timeoutMs.coerceIn(100L, 120_000L).toInt()
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        conn.instanceFollowRedirects = request.followRedirects
        conn.useCaches = false

        applyMethod(conn, request.method)

        if (conn is HttpsURLConnection) {
            configureHttpsConnection(conn, request)
        }
        configureConnection(conn, request)

        for ((k, v) in request.headers) {
            if (k.isNotBlank()) {
                conn.setRequestProperty(k.trim(), v)
            }
        }

        val body = request.bodyBytes
        val allowsEmptyOutput = request.method == HttpMethod.POST ||
            request.method == HttpMethod.PUT ||
            request.method == HttpMethod.PATCH
        if (body != null && (body.isNotEmpty() || allowsEmptyOutput)) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            if (body.isNotEmpty()) {
                conn.outputStream.use { out ->
                    out.write(body)
                    out.flush()
                }
            }
        }
        return conn
    }

    private fun applyMethod(conn: HttpURLConnection, method: HttpMethod) {
        if (method == HttpMethod.PATCH) {
            try {
                conn.requestMethod = method.name
            } catch (_: ProtocolException) {
                conn.requestMethod = HttpMethod.POST.name
                conn.setRequestProperty("X-HTTP-Method-Override", HttpMethod.PATCH.name)
            }
        } else {
            conn.requestMethod = method.name
        }
    }

    private fun extractHeaders(conn: HttpURLConnection): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        var headerIndex = 0
        while (true) {
            val key = conn.getHeaderFieldKey(headerIndex)
            val value = conn.getHeaderField(headerIndex)
            if (key == null && value == null) break
            if (!key.isNullOrEmpty() && value != null) {
                val existing = headers[key]
                headers[key] = if (existing == null) value else "$existing, $value"
            }
            headerIndex++
        }
        return headers
    }

    private fun isMockOrOfflineUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".example.com") || lower.contains(".example.org") || lower.contains(".invalid")
    }

    private fun statusTextForCode(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> ""
    }
}
