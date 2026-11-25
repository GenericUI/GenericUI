@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpConnectionNativeImpl
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume

class AppleHttpClientNativeImpl(
    private val allowLocalhostFallback: Boolean = false,
    private val defaultHandler: (suspend (HttpRequest) -> HttpResponse?)? = null
) : HttpClientNativeImpl {
    private val lock = AppleReentrantLock()
    private val routes = LinkedHashMap<String, suspend (HttpRequest) -> HttpResponse>()
    private val redirectPolicyByTaskId = HashMap<ULong, Boolean>()
    private var disposedState = false

    private val sessionDelegate = RedirectPolicySessionDelegate(lock, redirectPolicyByTaskId)
    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration,
        delegate = sessionDelegate,
        delegateQueue = null
    )

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun registerRoute(urlPattern: String, handler: suspend (HttpRequest) -> HttpResponse) {
        lock.withLock {
            if (!disposedState) {
                routes[urlPattern] = handler
            }
        }
    }

    override fun unregisterRoute(urlPattern: String) {
        lock.withLock {
            routes.remove(urlPattern)
        }
    }

    override fun clearRoutes() {
        lock.withLock {
            routes.clear()
        }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        if (isDisposed) {
            return AppleHttpResponseNativeImpl(
                statusCode = 0,
                statusText = "HttpClient is disposed",
                headers = emptyMap(),
                initialBodyBytes = ByteArray(0),
                url = request.url,
                byteOrder = request.byteOrder
            )
        }

        findRouteHandler(request.url)?.let { handler ->
            return ensureAppleResponse(handler(request))
        }

        if (!allowLocalhostFallback && defaultHandler != null) {
            defaultHandler.invoke(request)?.let { return ensureAppleResponse(it) }
        } else if (allowLocalhostFallback && !isLocalhostUrl(request.url) && defaultHandler != null) {
            defaultHandler.invoke(request)?.let { return ensureAppleResponse(it) }
        }

        val liveResult = executeViaNsUrlSession(request)
        if (liveResult.response != null) {
            return liveResult.response
        }

        if (allowLocalhostFallback && isLocalhostUrl(request.url) && defaultHandler != null) {
            defaultHandler.invoke(request)?.let { return ensureAppleResponse(it) }
        }

        return AppleHttpResponseNativeImpl(
            statusCode = 0,
            statusText = liveResult.errorDescription ?: "HTTP request failed",
            headers = emptyMap(),
            initialBodyBytes = ByteArray(0),
            url = request.url,
            byteOrder = request.byteOrder
        )
    }

    private fun ensureAppleResponse(resp: HttpResponse): AppleHttpResponseNativeImpl =
        resp as? AppleHttpResponseNativeImpl ?: AppleHttpResponseNativeImpl(
            statusCode = resp.statusCode,
            statusText = resp.statusText,
            headers = resp.headers,
            initialBodyBytes = resp.bodyBytes,
            url = resp.url,
            byteOrder = resp.byteOrder
        )

    override suspend fun openConnection(request: HttpRequest): HttpConnectionNativeImpl {
        val appleReq = request as? AppleHttpRequestNativeImpl ?: AppleHttpRequestNativeImpl(
            url = request.url,
            method = request.method,
            headers = request.headers,
            initialBodyBytes = request.bodyBytes,
            timeoutMs = request.timeoutMs,
            followRedirects = request.followRedirects,
            byteOrder = request.byteOrder
        )
        val response = execute(appleReq)
        return AppleHttpConnectionNativeImpl(appleReq, response)
    }

    override fun dispose() {
        val shouldInvalidate = lock.withLock {
            if (disposedState) false else {
                disposedState = true
                routes.clear()
                redirectPolicyByTaskId.clear()
                true
            }
        }
        if (shouldInvalidate) {
            session.invalidateAndCancel()
        }
    }

    private fun findRouteHandler(url: String): (suspend (HttpRequest) -> HttpResponse)? {
        return lock.withLock {
            routes[url]
                ?: routes[url.substringBefore('?')]
                ?: routes.entries.firstOrNull { (pattern, _) -> matchesPattern(pattern, url) }?.value
        } ?: DefaultHttpClientNativeImpl.findStaticRouteHandler(url)
    }

    private fun matchesPattern(pattern: String, url: String): Boolean {
        if (pattern == "*") return true
        if (pattern.endsWith("*")) {
            val prefix = pattern.dropLast(1)
            return url.startsWith(prefix)
        }
        return url == pattern || url.substringBefore('?') == pattern
    }

    private fun isLocalhostUrl(url: String): Boolean {
        val afterScheme = url.substringAfter("://", "")
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        val host = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0"
    }

    private suspend fun executeViaNsUrlSession(request: HttpRequest): NsUrlExecutionResult {
        val nsUrl = NSURL.URLWithString(request.url)
            ?: return NsUrlExecutionResult(null, "Invalid URL: ${request.url}")

        val timeoutSec = request.timeoutMs.coerceAtLeast(1L).toDouble() / 1000.0
        val nsRequest = NSMutableURLRequest.requestWithURL(
            URL = nsUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = timeoutSec
        )
        nsRequest.setHTTPMethod(request.method.name)
        for ((name, value) in request.headers) {
            nsRequest.setValue(value, forHTTPHeaderField = name)
        }
        val body = request.bodyBytes
        if (body != null && (body.isNotEmpty() || request.method == HttpMethod.POST || request.method == HttpMethod.PUT || request.method == HttpMethod.PATCH)) {
            nsRequest.setHTTPBody(AppleDataBridge.byteArrayToNSData(body))
        }

        return suspendCancellableCoroutine { continuation ->
            val completionLock = AppleReentrantLock()
            var completed = false
            var createdTask: NSURLSessionTask? = null

            val task = session.dataTaskWithRequest(nsRequest) { data: NSData?, urlResponse: NSURLResponse?, error: NSError? ->
                val taskId = createdTask?.taskIdentifier
                if (taskId != null) {
                    lock.withLock { redirectPolicyByTaskId.remove(taskId) }
                }
                val canResume = completionLock.withLock {
                    if (completed) false else {
                        completed = true
                        true
                    }
                }
                if (!canResume) return@dataTaskWithRequest

                val result = buildExecutionResult(request, data, urlResponse, error)
                continuation.resume(result)
            }
            createdTask = task
            lock.withLock {
                redirectPolicyByTaskId[task.taskIdentifier] = request.followRedirects
            }

            continuation.invokeOnCancellation {
                val shouldCancel = completionLock.withLock {
                    if (completed) false else {
                        completed = true
                        true
                    }
                }
                lock.withLock { redirectPolicyByTaskId.remove(task.taskIdentifier) }
                if (shouldCancel) {
                    task.cancel()
                }
            }

            task.resume()
        }
    }

    private fun buildExecutionResult(
        request: HttpRequest,
        data: NSData?,
        urlResponse: NSURLResponse?,
        error: NSError?
    ): NsUrlExecutionResult {
        if (error != null) {
            return NsUrlExecutionResult(null, error.localizedDescription)
        }

        val responseBytes = AppleDataBridge.nsDataToByteArray(data)
        val httpResp = urlResponse as? NSHTTPURLResponse
        if (httpResp != null) {
            val statusCode = httpResp.statusCode.toInt()
            val statusText = NSHTTPURLResponse.localizedStringForStatusCode(httpResp.statusCode)
            val mappedHeaders = LinkedHashMap<String, String>()
            for ((k, v) in httpResp.allHeaderFields) {
                val keyStr = k?.toString() ?: continue
                val valStr = v?.toString() ?: continue
                mappedHeaders[keyStr] = valStr
            }
            val finalUrl = httpResp.URL?.absoluteString ?: request.url
            return NsUrlExecutionResult(
                AppleHttpResponseNativeImpl(
                    statusCode = statusCode,
                    statusText = statusText,
                    headers = mappedHeaders,
                    initialBodyBytes = responseBytes,
                    url = finalUrl,
                    byteOrder = request.byteOrder
                ),
                null
            )
        }

        // Non-HTTP scheme (e.g. file:// or data: URL loaded via NSURLSession)
        val finalUrl = urlResponse?.URL?.absoluteString ?: request.url
        val statusCode = if (urlResponse != null) 200 else 0
        val statusText = if (urlResponse != null) "OK" else "No URL response"
        return NsUrlExecutionResult(
            if (urlResponse != null) {
                AppleHttpResponseNativeImpl(
                    statusCode = statusCode,
                    statusText = statusText,
                    headers = emptyMap(),
                    initialBodyBytes = responseBytes,
                    url = finalUrl,
                    byteOrder = request.byteOrder
                )
            } else {
                null
            },
            if (urlResponse == null) statusText else null
        )
    }

    private class NsUrlExecutionResult(
        val response: HttpResponse?,
        val errorDescription: String?
    )

    private class RedirectPolicySessionDelegate(
        private val lock: AppleReentrantLock,
        private val redirectPolicyByTaskId: HashMap<ULong, Boolean>
    ) : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit
        ) {
            val follow = lock.withLock { redirectPolicyByTaskId[task.taskIdentifier] ?: true }
            completionHandler(if (follow) newRequest else null)
        }
    }
}
