@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketMessageTypeData
import platform.Foundation.NSURLSessionWebSocketMessageTypeString
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.setValue
import platform.darwin.DISPATCH_SOURCE_TYPE_TIMER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.NSObject
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_source_cancel
import platform.darwin.dispatch_source_create
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_set_timer
import platform.darwin.dispatch_source_t
import platform.darwin.dispatch_time
import platform.posix.QOS_CLASS_DEFAULT
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class AppleWebSocketNativeImpl(
    private val connectTimeoutMs: Long = 15_000L,
    private val enableAutoPing: Boolean = false,
    private val pingIntervalMs: Long = 30_000L
) : WebSocketNativeImpl {
    private val lock = AppleReentrantLock()

    private var onOpenListener: (() -> Unit)? = null
    private var onTextMessageListener: ((String) -> Unit)? = null
    private var onBinaryMessageListener: ((ByteArray) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null
    private var onCloseListener: ((code: Int, reason: String) -> Unit)? = null

    private var connectedState = false
    private var disposedState = false
    private var currentUrl: String? = null
    private var closeNotified = false

    private var session: NSURLSession? = null
    private var webSocketTask: NSURLSessionWebSocketTask? = null
    private var pendingConnectContinuation: Continuation<Boolean>? = null
    private var connectTimeoutSource: dispatch_source_t = null
    private var autoPingSource: dispatch_source_t = null

    override val isConnected: Boolean
        get() = lock.withLock { connectedState && !disposedState }

    override val url: String?
        get() = lock.withLock { currentUrl }

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun setOnOpen(listener: (() -> Unit)?) {
        lock.withLock { onOpenListener = listener }
    }

    override fun setOnTextMessage(listener: ((String) -> Unit)?) {
        lock.withLock { onTextMessageListener = listener }
    }

    override fun setOnBinaryMessage(listener: ((ByteArray) -> Unit)?) {
        lock.withLock { onBinaryMessageListener = listener }
    }

    override fun setOnError(listener: ((Throwable) -> Unit)?) {
        lock.withLock { onErrorListener = listener }
    }

    override fun setOnClose(listener: ((code: Int, reason: String) -> Unit)?) {
        lock.withLock { onCloseListener = listener }
    }

    override suspend fun connect(url: String, headers: Map<String, String>): Boolean {
        if (isDisposed) return false

        val parsedUrl = NSURL.URLWithString(url)
        val scheme = parsedUrl?.scheme?.lowercase()
        if (parsedUrl == null || (scheme != "ws" && scheme != "wss" && scheme != "http" && scheme != "https")) {
            lock.withLock { onErrorListener }?.invoke(IllegalArgumentException("Unsupported WebSocket URL: $url"))
            return false
        }

        // Tear down any previous connection cleanly before opening a new one.
        close(1000, "Reconnecting")

        val timeoutSec = connectTimeoutMs.coerceAtLeast(100L).toDouble() / 1000.0
        val request = NSMutableURLRequest.requestWithURL(
            URL = parsedUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = timeoutSec
        )
        for ((name, value) in headers) {
            request.setValue(value, forHTTPHeaderField = name)
        }

        val delegate = WebSocketSessionDelegate()
        val createdSession = NSURLSession.sessionWithConfiguration(
            configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration,
            delegate = delegate,
            delegateQueue = null
        )
        val task = createdSession.webSocketTaskWithRequest(request)

        return suspendCancellableCoroutine { continuation ->
            lock.withLock {
                if (disposedState) {
                    createdSession.invalidateAndCancel()
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
                currentUrl = url
                closeNotified = false
                connectedState = false
                session = createdSession
                webSocketTask = task
                pendingConnectContinuation = continuation
                startConnectTimeoutTimerLocked(task)
            }

            continuation.invokeOnCancellation {
                val toCancel = lock.withLock {
                    if (pendingConnectContinuation === continuation) {
                        pendingConnectContinuation = null
                        cancelConnectTimeoutLocked()
                        task
                    } else {
                        null
                    }
                }
                if (toCancel != null) {
                    toCancel.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
                    createdSession.invalidateAndCancel()
                }
            }

            task.resume()
        }
    }

    override suspend fun sendText(text: String): Boolean {
        val activeTask = lock.withLock {
            if (!connectedState || disposedState) null else webSocketTask
        } ?: return false

        val message = NSURLSessionWebSocketMessage(string = text)
        return sendWebSocketMessage(activeTask, message)
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        val activeTask = lock.withLock {
            if (!connectedState || disposedState) null else webSocketTask
        } ?: return false

        val message = NSURLSessionWebSocketMessage(data = AppleDataBridge.byteArrayToNSData(bytes))
        return sendWebSocketMessage(activeTask, message)
    }

    suspend fun sendPing(): Boolean {
        val activeTask = lock.withLock {
            if (!connectedState || disposedState) null else webSocketTask
        } ?: return false

        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            activeTask.sendPingWithPongReceiveHandler { error ->
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return@sendPingWithPongReceiveHandler
                if (error != null) {
                    lock.withLock { onErrorListener }?.invoke(IllegalStateException(error.localizedDescription))
                    continuation.resume(false)
                } else {
                    continuation.resume(true)
                }
            }
        }
    }

    override suspend fun close(code: Int, reason: String) {
        var activeTask: NSURLSessionWebSocketTask? = null
        var activeSession: NSURLSession? = null
        var pendingCont: Continuation<Boolean>? = null
        var closeListener: ((Int, String) -> Unit)? = null

        lock.withLock {
            cancelTimersLocked()
            activeTask = webSocketTask
            activeSession = session
            webSocketTask = null
            session = null
            connectedState = false
            pendingCont = pendingConnectContinuation
            pendingConnectContinuation = null
            closeListener = if (!closeNotified && activeTask != null) {
                closeNotified = true
                onCloseListener
            } else {
                null
            }
        }

        pendingCont?.resume(false)
        if (activeTask != null) {
            val reasonData = if (reason.isEmpty()) null else AppleDataBridge.byteArrayToNSData(reason.encodeToByteArray())
            activeTask.cancelWithCloseCode(code.toLong(), reason = reasonData)
        }
        activeSession?.invalidateAndCancel()
        closeListener?.invoke(code, reason)
    }

    override fun dispose() {
        var activeTask: NSURLSessionWebSocketTask? = null
        var activeSession: NSURLSession? = null
        var pendingCont: Continuation<Boolean>? = null

        lock.withLock {
            if (disposedState) return
            disposedState = true
            connectedState = false
            cancelTimersLocked()
            activeTask = webSocketTask
            activeSession = session
            webSocketTask = null
            session = null
            pendingCont = pendingConnectContinuation
            pendingConnectContinuation = null
        }

        pendingCont?.resume(false)
        activeTask?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
        activeSession?.invalidateAndCancel()
    }

    private suspend fun sendWebSocketMessage(
        task: NSURLSessionWebSocketTask,
        message: NSURLSessionWebSocketMessage
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            task.sendMessage(message) { error ->
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return@sendMessage
                if (error != null) {
                    lock.withLock { onErrorListener }?.invoke(IllegalStateException(error.localizedDescription))
                    continuation.resume(false)
                } else {
                    continuation.resume(true)
                }
            }
        }
    }

    private fun startReceiveLoop(task: NSURLSessionWebSocketTask) {
        val stillActive = lock.withLock { webSocketTask === task && connectedState && !disposedState }
        if (!stillActive) return

        task.receiveMessageWithCompletionHandler { message: NSURLSessionWebSocketMessage?, error: NSError? ->
            if (error != null) {
                val shouldReport = lock.withLock { webSocketTask === task && connectedState && !disposedState }
                if (shouldReport) {
                    lock.withLock { onErrorListener }?.invoke(IllegalStateException(error.localizedDescription))
                }
                return@receiveMessageWithCompletionHandler
            }

            if (message != null) {
                when (message.type) {
                    NSURLSessionWebSocketMessageTypeString -> {
                        val text = message.string
                        if (text != null) {
                            lock.withLock { onTextMessageListener }?.invoke(text)
                        }
                    }
                    NSURLSessionWebSocketMessageTypeData -> {
                        val bytes = AppleDataBridge.nsDataToByteArray(message.data)
                        lock.withLock { onBinaryMessageListener }?.invoke(bytes)
                    }
                }
                val continueLoop = lock.withLock { webSocketTask === task && connectedState && !disposedState }
                if (continueLoop) {
                    startReceiveLoop(task)
                }
            }
        }
    }

    private fun startConnectTimeoutTimerLocked(task: NSURLSessionWebSocketTask) {
        cancelConnectTimeoutLocked()
        val timeoutMs = connectTimeoutMs.coerceAtLeast(100L)
        val queue = dispatch_get_global_queue(QOS_CLASS_DEFAULT.toLong(), 0uL)
        val timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0uL, 0uL, queue)
        if (timer != null) {
            val delayNs = timeoutMs.toULong() * NSEC_PER_MSEC
            dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, delayNs.toLong()), ULong.MAX_VALUE, delayNs / 10uL)
            dispatch_source_set_event_handler(timer) {
                val timedOut = lock.withLock {
                    if (webSocketTask === task && !connectedState && pendingConnectContinuation != null) {
                        val cont = pendingConnectContinuation
                        pendingConnectContinuation = null
                        cancelConnectTimeoutLocked()
                        val errListener = onErrorListener
                        val activeSess = session
                        webSocketTask = null
                        session = null
                        Triple(cont, errListener, activeSess)
                    } else {
                        null
                    }
                }
                if (timedOut != null) {
                    val (cont, errListener, activeSess) = timedOut
                    task.cancel()
                    activeSess?.invalidateAndCancel()
                    errListener?.invoke(IllegalStateException("WebSocket connection timed out after ${timeoutMs}ms"))
                    cont?.resume(false)
                }
            }
            connectTimeoutSource = timer
            dispatch_resume(timer)
        }
    }

    private fun startAutoPingLocked(task: NSURLSessionWebSocketTask) {
        if (!enableAutoPing) return
        cancelAutoPingLocked()
        val intervalMs = pingIntervalMs.coerceAtLeast(100L)
        val intervalNs = intervalMs.toULong() * NSEC_PER_MSEC
        val queue = dispatch_get_global_queue(QOS_CLASS_DEFAULT.toLong(), 0uL)
        val timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0uL, 0uL, queue)
        if (timer != null) {
            dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, intervalNs.toLong()), intervalNs, intervalNs / 10uL)
            dispatch_source_set_event_handler(timer) {
                val active = lock.withLock { webSocketTask === task && connectedState && !disposedState }
                if (active) {
                    task.sendPingWithPongReceiveHandler { pingError ->
                        if (pingError != null) {
                            val report = lock.withLock {
                                if (webSocketTask === task && connectedState && !disposedState) onErrorListener else null
                            }
                            report?.invoke(IllegalStateException(pingError.localizedDescription))
                        }
                    }
                }
            }
            autoPingSource = timer
            dispatch_resume(timer)
        }
    }

    private fun cancelConnectTimeoutLocked() {
        connectTimeoutSource?.let { dispatch_source_cancel(it) }
        connectTimeoutSource = null
    }

    private fun cancelAutoPingLocked() {
        autoPingSource?.let { dispatch_source_cancel(it) }
        autoPingSource = null
    }

    private fun cancelTimersLocked() {
        cancelConnectTimeoutLocked()
        cancelAutoPingLocked()
    }

    private inner class WebSocketSessionDelegate :
        NSObject(),
        NSURLSessionWebSocketDelegateProtocol,
        NSURLSessionTaskDelegateProtocol {

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didOpenWithProtocol: String?
        ) {
            val openPayload = lock.withLock {
                if (this@AppleWebSocketNativeImpl.webSocketTask !== webSocketTask || disposedState) {
                    null
                } else {
                    cancelConnectTimeoutLocked()
                    connectedState = true
                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    startAutoPingLocked(webSocketTask)
                    cont to onOpenListener
                }
            } ?: return

            openPayload.first?.resume(true)
            openPayload.second?.invoke()
            startReceiveLoop(webSocketTask)
        }

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didCloseWithCode: Long,
            reason: NSData?
        ) {
            val closePayload = lock.withLock {
                if (this@AppleWebSocketNativeImpl.webSocketTask !== webSocketTask) {
                    null
                } else {
                    cancelTimersLocked()
                    connectedState = false
                    this@AppleWebSocketNativeImpl.webSocketTask = null
                    val activeSess = this@AppleWebSocketNativeImpl.session
                    this@AppleWebSocketNativeImpl.session = null
                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    val listener = if (!closeNotified) {
                        closeNotified = true
                        onCloseListener
                    } else {
                        null
                    }
                    Triple(cont, listener, activeSess)
                }
            } ?: return

            val reasonStr = AppleDataBridge.nsDataToByteArray(reason).decodeToString()
            closePayload.first?.resume(false)
            closePayload.third?.invalidateAndCancel()
            closePayload.second?.invoke(didCloseWithCode.toInt(), reasonStr)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            val payload = lock.withLock {
                if (this@AppleWebSocketNativeImpl.webSocketTask !== task) {
                    null
                } else {
                    cancelTimersLocked()
                    connectedState = false
                    this@AppleWebSocketNativeImpl.webSocketTask = null
                    val activeSess = this@AppleWebSocketNativeImpl.session
                    this@AppleWebSocketNativeImpl.session = null
                    val cont = pendingConnectContinuation
                    pendingConnectContinuation = null
                    val errListener = if (didCompleteWithError != null) onErrorListener else null
                    val closeListener = if (!closeNotified) {
                        closeNotified = true
                        onCloseListener
                    } else {
                        null
                    }
                    arrayOf<Any?>(cont, errListener, closeListener, activeSess)
                }
            } ?: return

            @Suppress("UNCHECKED_CAST")
            val cont = payload[0] as? Continuation<Boolean>
            @Suppress("UNCHECKED_CAST")
            val errListener = payload[1] as? ((Throwable) -> Unit)
            @Suppress("UNCHECKED_CAST")
            val closeListener = payload[2] as? ((Int, String) -> Unit)
            val activeSess = payload[3] as? NSURLSession

            if (didCompleteWithError != null) {
                errListener?.invoke(IllegalStateException(didCompleteWithError.localizedDescription))
            }
            cont?.resume(false)
            activeSess?.invalidateAndCancel()
            closeListener?.invoke(
                if (didCompleteWithError != null) 1006 else 1000,
                didCompleteWithError?.localizedDescription ?: ""
            )
        }
    }
}
