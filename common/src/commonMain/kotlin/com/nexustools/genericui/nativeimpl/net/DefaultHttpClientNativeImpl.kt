package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Default cross-platform fallback implementation of [HttpClientNativeImpl].
 * Supports in-memory mock route registration (`registerRoute`) for deterministic testing.
 */
open class DefaultHttpClientNativeImpl : HttpClientNativeImpl {

    private val instanceRoutes = LinkedHashMap<String, suspend (HttpRequest) -> HttpResponse>()

    override var isDisposed: Boolean = false
        protected set

    override fun registerRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {
        instanceRoutes[urlPattern] = handler
        companionRoutes[urlPattern] = handler
    }

    override fun unregisterRoute(urlPattern: String) {
        instanceRoutes.remove(urlPattern)
        companionRoutes.remove(urlPattern)
    }

    override fun clearRoutes() {
        instanceRoutes.clear()
        companionRoutes.clear()
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        if (isDisposed) {
            return HttpResponse(
                statusCode = 499,
                statusText = "Client Closed Request",
                url = request.url
            )
        }
        val handler = findRouteHandler(request.url)
        if (handler != null) {
            return handler(request)
        }
        return HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to (request.header("Accept") ?: "text/plain; charset=utf-8")),
            bodyBytes = request.bodyBytes ?: ByteArray(0),
            url = request.url
        )
    }

    override suspend fun openConnection(request: HttpRequest): HttpConnectionNativeImpl =
        DefaultHttpConnectionNativeImpl(request, execute(request))

    override fun dispose() {
        isDisposed = true
        instanceRoutes.clear()
    }

    protected fun findRouteHandler(url: String): (suspend (HttpRequest) -> HttpResponse)? {
        instanceRoutes[url]?.let { return it }
        companionRoutes[url]?.let { return it }
        for ((pattern, handler) in instanceRoutes) {
            if (matchesPattern(pattern, url)) return handler
        }
        for ((pattern, handler) in companionRoutes) {
            if (matchesPattern(pattern, url)) return handler
        }
        return null
    }

    private fun matchesPattern(pattern: String, url: String): Boolean {
        if (pattern == "*" || pattern == url) return true
        if (pattern.endsWith("*")) {
            val prefix = pattern.removeSuffix("*")
            return url.startsWith(prefix)
        }
        return false
    }

    companion object {
        private val companionRoutes = LinkedHashMap<String, suspend (HttpRequest) -> HttpResponse>()

        fun registerStaticRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {
            companionRoutes[urlPattern] = handler
        }

        fun unregisterStaticRoute(urlPattern: String) {
            companionRoutes.remove(urlPattern)
        }

        fun clearStaticRoutes() {
            companionRoutes.clear()
        }
    }
}
