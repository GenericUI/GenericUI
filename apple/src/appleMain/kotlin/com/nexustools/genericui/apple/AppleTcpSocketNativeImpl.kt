@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Network.NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT
import platform.Network.NW_PARAMETERS_DEFAULT_CONFIGURATION
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_copy_current_path
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_state_waiting
import platform.Network.nw_connection_t
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_endpoint_get_port
import platform.Network.nw_error_get_error_code
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_path_copy_effective_local_endpoint
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.sec_protocol_options_set_tls_server_name
import platform.darwin.DISPATCH_SOURCE_TYPE_TIMER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_source_cancel
import platform.darwin.dispatch_source_create
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_set_timer
import platform.darwin.dispatch_time
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.EAGAIN
import platform.posix.ECONNREFUSED
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLERR
import platform.posix.POLLHUP
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_NOSIGPIPE
import platform.posix.addrinfo
import platform.posix.connect as posixConnect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.gai_strerror
import platform.posix.getaddrinfo
import platform.posix.getsockname
import platform.posix.getsockopt
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6
import platform.posix.sockaddr_storage
import platform.posix.socket
import platform.posix.strerror
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class AppleTcpSocketNativeImpl : TcpSocketNativeImpl {
    private val state = AppleStreamState()
    private val ioQueue = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)
    private val tlsQueue = dispatch_queue_create("com.nexustools.genericui.apple.tcp.tls", null)

    private var socketFd: Int = -1
    private var nwConnection: nw_connection_t = null
    private var useTls: Boolean = false
    private var connectedState: Boolean = false
    private var remoteHostState: String? = null
    private var remotePortState: Int = 0
    private var localPortState: Int = 0
    private var inFlightTlsSends: Int = 0
    private val pendingPlainWrites = ArrayDeque<ByteArray>()
    private val outboundHistory = ArrayList<ByteArray>()

    override val isConnected: Boolean
        get() = state.lock.withLock { connectedState && !state.isDisposed }

    override val remoteHost: String?
        get() = state.lock.withLock { remoteHostState }

    override val remotePort: Int
        get() = state.lock.withLock { remotePortState }

    override val localPort: Int
        get() = state.lock.withLock { localPortState }

    override val isReadable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isEnded && (connectedState || state.hasPushback()) }

    override val isPaused: Boolean
        get() = state.lock.withLock { state.isPaused }

    override val isEnded: Boolean
        get() = state.lock.withLock { state.isEnded }

    override val bytesRead: Long
        get() = state.lock.withLock { state.bytesRead }

    override val isWritable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isFinished && connectedState }

    override val isFinished: Boolean
        get() = state.lock.withLock { state.isFinished }

    override val bytesWritten: Long
        get() = state.lock.withLock { state.bytesWritten }

    override var highWaterMark: Int
        get() = state.lock.withLock { state.highWaterMark }
        set(value) {
            state.lock.withLock { state.highWaterMark = value }
        }

    override var byteOrder: ByteOrder
        get() = state.lock.withLock { state.byteOrder }
        set(value) {
            state.lock.withLock { state.byteOrder = value }
        }

    override val isDisposed: Boolean
        get() = state.lock.withLock { state.isDisposed }

    override suspend fun connect(host: String, port: Int, timeoutMs: Long, tls: Boolean): Boolean {
        if (isDisposed || host.isBlank() || port <= 0 || port > 65535) {
            state.emitError(IllegalArgumentException("Invalid TCP endpoint: $host:$port"))
            return false
        }

        closeTransportOnly()
        state.lock.withLock {
            state.isEnded = false
            state.isFinished = false
            useTls = tls
        }

        return if (tls) {
            connectTls(host, port, timeoutMs)
        } else {
            connectPlaintext(host, port, timeoutMs)
        }
    }

    private suspend fun connectPlaintext(host: String, port: Int, timeoutMs: Long): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            var cancelled = false

            fun finish(ok: Boolean, err: Throwable? = null) {
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return
                if (err != null) state.emitError(err)
                continuation.resume(ok)
            }

            continuation.invokeOnCancellation {
                resumeLock.withLock { cancelled = true }
            }

            dispatch_async(ioQueue) {
                memScoped {
                    val hints = alloc<addrinfo>()
                    memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
                    hints.ai_family = AF_UNSPEC
                    hints.ai_socktype = SOCK_STREAM

                    val resPtr = alloc<CPointerVar<addrinfo>>()
                    val gai = getaddrinfo(host, port.toString(), hints.ptr, resPtr.ptr)
                    if (gai != 0) {
                        val msg = gai_strerror(gai)?.toKString() ?: "DNS resolution failed ($gai)"
                        finish(false, IllegalStateException("TCP resolve failed for $host:$port: $msg"))
                        return@dispatch_async
                    }

                    val head = resPtr.value
                    try {
                        var current = head
                        var connectedFd = -1
                        var lastErrMsg = "Connection failed"
                        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.coerceAtLeast(100L).milliseconds

                        while (current != null && !cancelled && !isDisposed) {
                            val info = current.pointed
                            val fd = socket(info.ai_family, info.ai_socktype, info.ai_protocol)
                            if (fd >= 0) {
                                enableNoSigPipe(fd)
                                setNonBlocking(fd, true)

                                val rc = posixConnect(fd, info.ai_addr, info.ai_addrlen)
                                if (rc == 0) {
                                    connectedFd = fd
                                    break
                                }
                                val err = errno
                                if (err == EINPROGRESS) {
                                    val pfd = allocArray<pollfd>(1)
                                    var waitOk = false
                                    while (!cancelled && !isDisposed) {
                                        val remainingMs = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
                                        if (remainingMs <= 0L) {
                                            lastErrMsg = "Timed out connecting to $host:$port"
                                            break
                                        }
                                        pfd[0].fd = fd
                                        pfd[0].events = POLLOUT.toShort()
                                        pfd[0].revents = 0
                                        val slice = minOf(remainingMs, 50L).toInt().coerceAtLeast(1)
                                        val pr = poll(pfd, 1u, slice)
                                        if (pr > 0) {
                                            val soErr = alloc<IntVar>()
                                            val soLen = alloc<UIntVar>()
                                            soLen.value = sizeOf<IntVar>().toUInt()
                                            getsockopt(fd, SOL_SOCKET, SO_ERROR, soErr.ptr, soLen.ptr)
                                            if (soErr.value == 0) {
                                                waitOk = true
                                            } else {
                                                lastErrMsg = strerror(soErr.value)?.toKString() ?: "SO_ERROR=${soErr.value}"
                                            }
                                            break
                                        } else if (pr < 0 && errno != EINTR) {
                                            lastErrMsg = strerror(errno)?.toKString() ?: "poll error"
                                            break
                                        }
                                    }
                                    if (waitOk) {
                                        connectedFd = fd
                                        break
                                    }
                                } else {
                                    lastErrMsg = strerror(err)?.toKString() ?: "connect errno=$err"
                                }
                                platform.posix.close(fd)
                            } else {
                                lastErrMsg = strerror(errno)?.toKString() ?: "socket errno=$errno"
                            }
                            current = info.ai_next
                        }

                        if (connectedFd < 0 || cancelled || isDisposed) {
                            if (connectedFd >= 0) platform.posix.close(connectedFd)
                            finish(false, IllegalStateException("TCP connect to $host:$port failed: $lastErrMsg"))
                            return@dispatch_async
                        }

                        val boundLocalPort = queryLocalPort(connectedFd)
                        state.lock.withLock {
                            if (state.isDisposed) {
                                platform.posix.close(connectedFd)
                                finish(false, IllegalStateException("Socket disposed during connect"))
                                return@dispatch_async
                            }
                            socketFd = connectedFd
                            connectedState = true
                            remoteHostState = host
                            remotePortState = port
                            localPortState = boundLocalPort
                        }
                        finish(true)
                    } finally {
                        if (head != null) freeaddrinfo(head)
                    }
                }
            }
        }
    }

    private suspend fun connectTls(host: String, port: Int, timeoutMs: Long): Boolean {
        val endpoint = nw_endpoint_create_host(host, port.toString())
        val parameters = nw_parameters_create_secure_tcp(
            configure_tls = { tlsOptions ->
                val secOptions = nw_tls_copy_sec_protocol_options(tlsOptions)
                if (secOptions != null) {
                    sec_protocol_options_set_tls_server_name(secOptions, host)
                }
            },
            configure_tcp = NW_PARAMETERS_DEFAULT_CONFIGURATION
        )
        val conn = nw_connection_create(endpoint, parameters)
        if (conn == null) {
            state.emitError(IllegalStateException("Failed to create TLS nw_connection for $host:$port"))
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            val boundedTimeout = timeoutMs.coerceAtLeast(100L)
            val timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0uL, 0uL, tlsQueue)

            fun finish(ok: Boolean, errMsg: String? = null) {
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        timer?.let { dispatch_source_cancel(it) }
                        true
                    }
                }
                if (!canResume) return
                if (!ok) {
                    nw_connection_cancel(conn)
                    state.lock.withLock {
                        if (nwConnection === conn) {
                            nwConnection = null
                            connectedState = false
                        }
                    }
                    errMsg?.let { state.emitError(IllegalStateException(it)) }
                }
                continuation.resume(ok)
            }

            if (timer != null) {
                val delayNs = boundedTimeout.toULong() * NSEC_PER_MSEC
                dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, delayNs.toLong()), ULong.MAX_VALUE, delayNs / 10uL)
                dispatch_source_set_event_handler(timer) {
                    finish(false, "TLS connection to $host:$port timed out after ${boundedTimeout}ms")
                }
                dispatch_resume(timer)
            }

            state.lock.withLock {
                nwConnection = conn
                remoteHostState = host
                remotePortState = port
            }

            nw_connection_set_queue(conn, tlsQueue)
            nw_connection_set_state_changed_handler(conn) { connState, err ->
                when (connState) {
                    nw_connection_state_ready -> {
                        val localEpPort = nw_connection_copy_current_path(conn)
                            ?.let { nw_path_copy_effective_local_endpoint(it) }
                            ?.let { nw_endpoint_get_port(it).toInt() }
                            ?: 0
                        state.lock.withLock {
                            if (nwConnection === conn) {
                                connectedState = true
                                localPortState = localEpPort
                            }
                        }
                        finish(true)
                    }
                    nw_connection_state_waiting -> {
                        val code = if (err != null) nw_error_get_error_code(err) else 0
                        if (code == ECONNREFUSED) {
                            finish(false, "TLS connection refused for $host:$port")
                        }
                    }
                    nw_connection_state_failed -> {
                        val code = if (err != null) nw_error_get_error_code(err) else -1
                        finish(false, "TLS connection failed for $host:$port (error=$code)")
                    }
                    nw_connection_state_cancelled -> {
                        finish(false, "TLS connection cancelled for $host:$port")
                    }
                }
            }

            continuation.invokeOnCancellation {
                finish(false, "TLS connection cancelled")
            }

            nw_connection_start(conn)
        }
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (maxBytes <= 0 || isPaused || isDisposed || state.isEnded) return null
        state.pollPushback(maxBytes)?.let { return it }

        val (tls, conn, fd) = state.lock.withLock {
            Triple(useTls, nwConnection, socketFd)
        }
        if (!isConnected) return null

        return if (tls) {
            if (conn == null) null else readTls(conn, maxBytes)
        } else {
            if (fd < 0) null else readPlaintext(fd, maxBytes)
        }
    }

    private suspend fun readPlaintext(fd: Int, maxBytes: Int): ByteBuffer? {
        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            var cancelled = false

            fun finish(buf: ByteBuffer?, err: Throwable? = null) {
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return
                if (err != null) state.emitError(err)
                continuation.resume(buf)
            }

            continuation.invokeOnCancellation {
                resumeLock.withLock { cancelled = true }
            }

            dispatch_async(ioQueue) {
                val readCap = maxBytes.coerceIn(1, 256 * 1024)
                val buffer = ByteArray(readCap)
                memScoped {
                    val pfd = allocArray<pollfd>(1)
                    while (!cancelled && !isDisposed) {
                        val activeFd = state.lock.withLock { socketFd }
                        if (activeFd != fd || activeFd < 0 || !isConnected) {
                            finish(null)
                            return@dispatch_async
                        }
                        if (isPaused) {
                            finish(null)
                            return@dispatch_async
                        }
                        pfd[0].fd = fd
                        pfd[0].events = POLLIN.toShort()
                        pfd[0].revents = 0
                        val pr = poll(pfd, 1u, 50)
                        if (pr == 0) continue
                        if (pr < 0) {
                            if (errno == EINTR) continue
                            finish(null, IllegalStateException("TCP poll error: ${strerror(errno)?.toKString() ?: errno}"))
                            return@dispatch_async
                        }
                        val n = buffer.usePinned { pinned ->
                            recv(fd, pinned.addressOf(0), readCap.toULong(), 0).toInt()
                        }
                        when {
                            n > 0 -> {
                                val slice = buffer.copyOf(n)
                                val chunk = ByteBuffer.wrap(slice, byteOrder = byteOrder)
                                state.recordRead(n)
                                finish(chunk)
                                return@dispatch_async
                            }
                            n == 0 -> {
                                state.lock.withLock {
                                    connectedState = false
                                    state.isEnded = true
                                }
                                finish(null)
                                return@dispatch_async
                            }
                            else -> {
                                val err = errno
                                if (err == EAGAIN || err == EWOULDBLOCK || err == EINTR) {
                                    continue
                                }
                                state.lock.withLock {
                                    connectedState = false
                                    state.isEnded = true
                                }
                                finish(null, IllegalStateException("TCP recv error: ${strerror(err)?.toKString() ?: err}"))
                                return@dispatch_async
                            }
                        }
                    }
                    finish(null)
                }
            }
        }
    }

    private suspend fun readTls(conn: nw_connection_t, maxBytes: Int): ByteBuffer? {
        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false

            fun finish(buf: ByteBuffer?, err: Throwable? = null) {
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return
                if (err != null) state.emitError(err)
                continuation.resume(buf)
            }

            val cap = maxBytes.coerceIn(1, 256 * 1024).toUInt()
            nw_connection_receive(conn, 1u, cap) { content, _, isComplete, err ->
                if (err != null) {
                    val code = nw_error_get_error_code(err)
                    state.lock.withLock {
                        connectedState = false
                        state.isEnded = true
                    }
                    finish(null, IllegalStateException("TLS recv failed (error=$code)"))
                    return@nw_connection_receive
                }
                val bytes = AppleDataBridge.dispatchDataToByteArray(content)
                if (bytes.isNotEmpty()) {
                    state.recordRead(bytes.size)
                    if (isComplete) {
                        state.lock.withLock { state.isEnded = true }
                    }
                    finish(ByteBuffer.wrap(bytes, byteOrder = byteOrder))
                } else {
                    state.lock.withLock {
                        if (isComplete) {
                            connectedState = false
                            state.isEnded = true
                        }
                    }
                    finish(null)
                }
            }
        }
    }

    override fun write(buffer: ByteBuffer): Boolean {
        val slice = buffer.remainingByteArray()
        return write(slice, 0, slice.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice offset=$offset length=$length size=${chunk.size}"
        }
        val slice = if (offset == 0 && length == chunk.size) chunk.copyOf() else chunk.copyOfRange(offset, offset + length)
        var writable = false
        var tls = false
        var conn: nw_connection_t = null
        var fd = -1
        state.lock.withLock {
            writable = !state.isDisposed && !state.isFinished && connectedState
            tls = useTls
            conn = nwConnection
            fd = socketFd
        }
        if (!writable) return false
        if (slice.isEmpty()) return true

        val wrapped = ByteBuffer.wrap(slice, byteOrder = byteOrder)
        if (tls) {
            val activeConn = conn ?: return false
            val dispatchData = AppleDataBridge.byteArrayToDispatchData(slice) ?: return false
            state.lock.withLock {
                inFlightTlsSends++
                outboundHistory.add(slice)
            }
            state.recordWrite(slice.size)
            state.emitWrite(wrapped)
            nw_connection_send(activeConn, dispatchData, NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT, false) { err ->
                state.lock.withLock {
                    if (inFlightTlsSends > 0) inFlightTlsSends--
                }
                if (err != null) {
                    val code = nw_error_get_error_code(err)
                    state.emitError(IllegalStateException("TLS send error ($code)"))
                }
            }
            return true
        }

        val activeFd = fd
        if (activeFd < 0) return false
        var sentOffset = 0
        state.lock.withLock {
            // Drain any previously queued EAGAIN tail first if possible, or enqueue behind it.
            if (pendingPlainWrites.isNotEmpty()) {
                pendingPlainWrites.addLast(slice)
                outboundHistory.add(slice)
                state.recordWrite(slice.size)
                state.emitWrite(wrapped)
                return false
            }
        }
        while (sentOffset < slice.size) {
            val n = slice.usePinned { pinned ->
                send(activeFd, pinned.addressOf(sentOffset), (slice.size - sentOffset).toULong(), 0).toInt()
            }
            if (n > 0) {
                sentOffset += n
            } else {
                val err = errno
                if (err == EINTR) continue
                if (err == EAGAIN || err == EWOULDBLOCK) {
                    val remainder = slice.copyOfRange(sentOffset, slice.size)
                    state.lock.withLock {
                        pendingPlainWrites.addLast(remainder)
                        outboundHistory.add(slice)
                    }
                    state.recordWrite(slice.size)
                    state.emitWrite(wrapped)
                    return false
                }
                state.emitError(IllegalStateException("TCP send failed: ${strerror(err)?.toKString() ?: err}"))
                return false
            }
        }

        state.lock.withLock { outboundHistory.add(slice) }
        state.recordWrite(slice.size)
        state.emitWrite(wrapped)
        return state.lock.withLock { pendingPlainWrites.isEmpty() }
    }

    override suspend fun flush() {
        val tls = state.lock.withLock { useTls }
        if (tls) {
            val conn = state.lock.withLock { nwConnection } ?: return
            // Barrier empty send waits until all prior nw_connection_send writes are handed off.
            suspendCancellableCoroutine<Unit> { cont ->
                nw_connection_send(conn, null, NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT, false) { _ ->
                    cont.resume(Unit)
                }
            }
            state.emitDrain()
            return
        }

        val fd = state.lock.withLock { socketFd }
        if (fd < 0) return
        val hasPending = state.lock.withLock { pendingPlainWrites.isNotEmpty() }
        if (!hasPending) {
            state.emitDrain()
            return
        }

        suspendCancellableCoroutine<Unit> { cont ->
            dispatch_async(ioQueue) {
                memScoped {
                    val pfd = allocArray<pollfd>(1)
                    while (!isDisposed) {
                        val chunk = state.lock.withLock { pendingPlainWrites.firstOrNull() } ?: break
                        var off = 0
                        var broken = false
                        while (off < chunk.size && !broken && !isDisposed) {
                            val n = chunk.usePinned { pinned ->
                                send(fd, pinned.addressOf(off), (chunk.size - off).toULong(), 0).toInt()
                            }
                            if (n > 0) {
                                off += n
                            } else {
                                val err = errno
                                if (err == EINTR) continue
                                if (err == EAGAIN || err == EWOULDBLOCK) {
                                    pfd[0].fd = fd
                                    pfd[0].events = POLLOUT.toShort()
                                    pfd[0].revents = 0
                                    val pr = poll(pfd, 1u, 100)
                                    if (pr < 0 && errno != EINTR) {
                                        broken = true
                                    } else if ((pfd[0].revents.toInt() and (POLLERR or POLLHUP)) != 0) {
                                        broken = true
                                    }
                                    continue
                                }
                                broken = true
                            }
                        }
                        state.lock.withLock {
                            if (pendingPlainWrites.firstOrNull() === chunk) {
                                pendingPlainWrites.removeFirst()
                            }
                            if (broken && off < chunk.size) {
                                pendingPlainWrites.clear()
                            }
                        }
                        if (broken) break
                    }
                }
                cont.resume(Unit)
            }
        }
        state.emitDrain()
    }

    override suspend fun end(finalBuffer: ByteBuffer?) {
        if (finalBuffer != null && finalBuffer.hasRemaining) {
            write(finalBuffer)
        }
        flush()
        state.emitFinish()
    }

    override suspend fun close() {
        flush()
        closeTransportOnly()
        state.emitFinish()
        state.emitEnd()
        state.markDisposedAndNotifyClose()
    }

    override fun dispose() {
        closeTransportOnly()
        state.markDisposedAndNotifyClose()
    }

    override fun onData(listener: suspend (ByteBuffer) -> Unit): Readable {
        state.addDataListener(listener)
        return this
    }

    override fun onEnd(listener: suspend () -> Unit): Readable {
        state.addEndListener(listener)
        return this
    }

    override fun onWrite(listener: (ByteBuffer) -> Unit): Writable {
        state.addWriteListener(listener)
        return this
    }

    override fun onDrain(listener: suspend () -> Unit): Writable {
        state.addDrainListener(listener)
        return this
    }

    override fun onFinish(listener: suspend () -> Unit): Writable {
        state.addFinishListener(listener)
        return this
    }

    override fun onError(listener: (Throwable) -> Unit): Stream {
        state.addErrorListener(listener)
        return this
    }

    override fun onClose(listener: () -> Unit): Stream {
        state.addCloseListener(listener)
        return this
    }

    override fun pause(): Readable {
        state.pause()
        return this
    }

    override fun resume(): Readable {
        state.resume()
        return this
    }

    override fun unshift(buffer: ByteBuffer) {
        state.unshift(buffer)
    }

    override fun toByteArray(): ByteArray {
        return state.lock.withLock {
            val total = outboundHistory.sumOf { it.size }
            val result = ByteArray(total)
            var offset = 0
            for (chunk in outboundHistory) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            result
        }
    }

    override fun toByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap(toByteArray(), byteOrder = byteOrder)
    }

    private fun closeTransportOnly() {
        var fdToClose = -1
        var connToCancel: nw_connection_t = null
        state.lock.withLock {
            fdToClose = socketFd
            socketFd = -1
            connToCancel = nwConnection
            nwConnection = null
            connectedState = false
            pendingPlainWrites.clear()
        }
        if (fdToClose >= 0) {
            platform.posix.close(fdToClose)
        }
        if (connToCancel != null) {
            nw_connection_cancel(connToCancel)
        }
    }

    private fun enableNoSigPipe(fd: Int) {
        memScoped {
            val flag = alloc<IntVar>()
            flag.value = 1
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, flag.ptr, sizeOf<IntVar>().toUInt())
        }
    }

    private fun setNonBlocking(fd: Int, nonBlocking: Boolean) {
        val currentFlags = fcntl(fd, F_GETFL, 0)
        if (currentFlags >= 0) {
            val updated = if (nonBlocking) (currentFlags or O_NONBLOCK) else (currentFlags and O_NONBLOCK.inv())
            fcntl(fd, F_SETFL, updated)
        }
    }

    private fun queryLocalPort(fd: Int): Int {
        return memScoped {
            val storage = alloc<sockaddr_storage>()
            val len = alloc<UIntVar>()
            len.value = sizeOf<sockaddr_storage>().toUInt()
            if (getsockname(fd, storage.ptr.reinterpret(), len.ptr) != 0) return 0
            when (storage.ss_family.toInt()) {
                AF_INET -> {
                    val sin = storage.ptr.reinterpret<sockaddr_in>().pointed
                    networkToHostUShort(sin.sin_port)
                }
                AF_INET6 -> {
                    val sin6 = storage.ptr.reinterpret<sockaddr_in6>().pointed
                    networkToHostUShort(sin6.sin6_port)
                }
                else -> 0
            }
        }
    }

    private fun networkToHostUShort(port: UShort): Int {
        val v = port.toInt() and 0xFFFF
        return ((v and 0xFF) shl 8) or ((v ushr 8) and 0xFF)
    }
}
