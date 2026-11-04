package com.nexustools.genericui.nativeimpl.net

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Per-backend native contract for 100% asynchronous HTTP/HTTPS requests.
 */
interface HttpClientNativeImpl : Disposable {
    /**
     * Executes [request] asynchronously and returns the [HttpResponse] readable stream.
     */
    suspend fun execute(request: HttpRequest): HttpResponse

    /**
     * Opens an [HttpConnectionNativeImpl] duplex stream (`Readable` + `Writable`) for [request].
     */
    suspend fun openConnection(request: HttpRequest): HttpConnectionNativeImpl =
        DefaultHttpConnectionNativeImpl(request, execute(request))

    fun registerRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {}

    fun unregisterRoute(urlPattern: String) {}

    fun clearRoutes() {}
}
