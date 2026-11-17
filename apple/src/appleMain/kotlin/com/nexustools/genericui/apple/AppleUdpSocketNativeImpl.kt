@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.net.UdpDatagram
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.AI_NUMERICHOST
import platform.posix.AI_PASSIVE
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.INADDR_ANY
import platform.posix.IPPROTO_IP
import platform.posix.IPPROTO_IPV6
import platform.posix.IPV6_JOIN_GROUP
import platform.posix.IPV6_LEAVE_GROUP
import platform.posix.IP_ADD_MEMBERSHIP
import platform.posix.IP_DROP_MEMBERSHIP
import platform.posix.IP_MULTICAST_IF
import platform.posix.NI_NUMERICHOST
import platform.posix.NI_NUMERICSERV
import platform.posix.O_NONBLOCK
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.SO_REUSEPORT
import platform.posix.addrinfo
import platform.posix.bind as posixBind
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.gai_strerror
import platform.posix.getaddrinfo
import platform.posix.getnameinfo
import platform.posix.getsockname
import platform.posix.if_nametoindex
import platform.posix.ip_mreq
import platform.posix.ipv6_mreq
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recvfrom
import platform.posix.sendto
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

class AppleUdpSocketNativeImpl : UdpSocketNativeImpl {
    private val state = AppleStreamState()
    private val ioQueue = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)

    private var socketFd: Int = -1
    private var socketFamily: Int = AF_INET
    private var boundState: Boolean = false
    private var localAddressState: String? = null
    private var localPortState: Int = 0
    private var remoteHostState: String? = null
    private var remotePortState: Int = 0
    private var broadcastState: Boolean = false
    private val joinedMulticastGroups = LinkedHashSet<String>()
    private val outboundHistory = ArrayList<ByteArray>()

    override val isBound: Boolean
        get() = state.lock.withLock { boundState && socketFd >= 0 && !state.isDisposed }

    override val localAddress: String?
        get() = state.lock.withLock { localAddressState }

    override val localPort: Int
        get() = state.lock.withLock { localPortState }

    override var remoteHost: String?
        get() = state.lock.withLock { remoteHostState }
        set(value) {
            state.lock.withLock { remoteHostState = value }
        }

    override var remotePort: Int
        get() = state.lock.withLock { remotePortState }
        set(value) {
            state.lock.withLock { remotePortState = value }
        }

    override var broadcastEnabled: Boolean
        get() = state.lock.withLock { broadcastState }
        set(value) {
            val fd = state.lock.withLock {
                broadcastState = value
                socketFd
            }
            if (fd >= 0) {
                setSocketBooleanOption(fd, SOL_SOCKET, SO_BROADCAST, value)
            }
        }

    override val isReadable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isEnded && (boundState || state.hasPushback()) }

    override val isPaused: Boolean
        get() = state.lock.withLock { state.isPaused }

    override val isEnded: Boolean
        get() = state.lock.withLock { state.isEnded }

    override val bytesRead: Long
        get() = state.lock.withLock { state.bytesRead }

    override val isWritable: Boolean
        get() = state.lock.withLock { !state.isDisposed && !state.isFinished }

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

    override fun supportsMulticast(): Boolean = true

    override suspend fun bind(localHost: String, localPort: Int): Boolean {
        if (isDisposed || localPort < 0 || localPort > 65535) return false
        closeSocketOnly()
        state.lock.withLock {
            state.isEnded = false
            state.isFinished = false
        }

        val hostArg = localHost.ifBlank { "0.0.0.0" }
        return memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
            hints.ai_family = if (hostArg.contains(':')) AF_INET6 else AF_INET
            hints.ai_socktype = SOCK_DGRAM
            hints.ai_flags = AI_PASSIVE

            val resPtr = alloc<CPointerVar<addrinfo>>()
            val gai = getaddrinfo(hostArg, localPort.toString(), hints.ptr, resPtr.ptr)
            if (gai != 0) {
                val msg = gai_strerror(gai)?.toKString() ?: "UDP bind getaddrinfo failed ($gai)"
                state.emitError(IllegalStateException(msg))
                return@memScoped false
            }

            val head = resPtr.value
            try {
                var current = head
                while (current != null) {
                    val info = current.pointed
                    val fd = socket(info.ai_family, info.ai_socktype, info.ai_protocol)
                    if (fd >= 0) {
                        setSocketBooleanOption(fd, SOL_SOCKET, SO_REUSEADDR, true)
                        setSocketBooleanOption(fd, SOL_SOCKET, SO_REUSEPORT, true)
                        setSocketBooleanOption(fd, SOL_SOCKET, SO_NOSIGPIPE, true)
                        if (broadcastEnabled) {
                            setSocketBooleanOption(fd, SOL_SOCKET, SO_BROADCAST, true)
                        }
                        setNonBlocking(fd, true)

                        val rc = posixBind(fd, info.ai_addr, info.ai_addrlen)
                        if (rc == 0) {
                            val resolvedLocal = queryLocalEndpoint(fd)
                            state.lock.withLock {
                                socketFd = fd
                                socketFamily = info.ai_family
                                boundState = true
                                localAddressState = resolvedLocal.first ?: hostArg
                                localPortState = if (resolvedLocal.second > 0) resolvedLocal.second else localPort
                            }
                            return@memScoped true
                        }
                        platform.posix.close(fd)
                    }
                    current = info.ai_next
                }
                state.emitError(IllegalStateException("UDP bind failed on $hostArg:$localPort: ${strerror(errno)?.toKString() ?: errno}"))
                false
            } finally {
                if (head != null) freeaddrinfo(head)
            }
        }
    }

    override suspend fun connect(remoteHost: String, remotePort: Int): Boolean {
        if (isDisposed || remoteHost.isBlank() || remotePort <= 0 || remotePort > 65535) return false
        val valid = memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_DGRAM
            val resPtr = alloc<CPointerVar<addrinfo>>()
            val gai = getaddrinfo(remoteHost, remotePort.toString(), hints.ptr, resPtr.ptr)
            if (gai == 0 && resPtr.value != null) {
                freeaddrinfo(resPtr.value)
                true
            } else {
                false
            }
        }
        if (!valid) {
            state.emitError(IllegalArgumentException("Unable to resolve UDP peer $remoteHost:$remotePort"))
            return false
        }
        if (!isBound) {
            val bindHost = if (remoteHost.contains(':')) "::" else "0.0.0.0"
            if (!bind(bindHost, 0)) return false
        }
        state.lock.withLock {
            remoteHostState = remoteHost
            remotePortState = remotePort
        }
        return true
    }

    override suspend fun joinMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        return configureMulticastMembership(multicastAddress, interfaceName, join = true)
    }

    override suspend fun leaveMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        return configureMulticastMembership(multicastAddress, interfaceName, join = false)
    }

    private suspend fun configureMulticastMembership(
        multicastAddress: String,
        interfaceName: String?,
        join: Boolean
    ): Boolean {
        if (isDisposed || multicastAddress.isBlank()) return false
        if (!isBound) {
            val defaultBind = if (multicastAddress.contains(':')) "::" else "0.0.0.0"
            if (!bind(defaultBind, 0)) return false
        }
        val fd = state.lock.withLock { socketFd }
        if (fd < 0) return false

        return memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_DGRAM
            hints.ai_flags = AI_NUMERICHOST
            val resPtr = alloc<CPointerVar<addrinfo>>()
            val gai = getaddrinfo(multicastAddress, null, hints.ptr, resPtr.ptr)
            if (gai != 0 || resPtr.value == null) {
                state.emitError(IllegalArgumentException("Invalid multicast address: $multicastAddress"))
                return@memScoped false
            }

            val head = resPtr.value!!
            try {
                val ai = head.pointed
                val rc = when (ai.ai_family) {
                    AF_INET -> {
                        val mreq = alloc<ip_mreq>()
                        val sin = ai.ai_addr!!.reinterpret<sockaddr_in>().pointed
                        mreq.imr_multiaddr.s_addr = sin.sin_addr.s_addr
                        val ifAddr = resolveIpv4InterfaceAddress(interfaceName)
                        mreq.imr_interface.s_addr = ifAddr
                        if (join && !interfaceName.isNullOrBlank()) {
                            setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF, mreq.imr_interface.ptr, sizeOf<platform.posix.in_addr>().toUInt())
                        }
                        val opt = if (join) IP_ADD_MEMBERSHIP else IP_DROP_MEMBERSHIP
                        setsockopt(fd, IPPROTO_IP, opt, mreq.ptr, sizeOf<ip_mreq>().toUInt())
                    }
                    AF_INET6 -> {
                        val mreq6 = alloc<ipv6_mreq>()
                        memset(mreq6.ptr, 0, sizeOf<ipv6_mreq>().toULong())
                        val sin6 = ai.ai_addr!!.reinterpret<sockaddr_in6>().pointed
                        memcpy(mreq6.ipv6mr_multiaddr.ptr, sin6.sin6_addr.ptr, sizeOf<platform.posix.in6_addr>().toULong())
                        mreq6.ipv6mr_interface = if (!interfaceName.isNullOrBlank()) if_nametoindex(interfaceName) else 0u
                        val opt = if (join) IPV6_JOIN_GROUP else IPV6_LEAVE_GROUP
                        setsockopt(fd, IPPROTO_IPV6, opt, mreq6.ptr, sizeOf<ipv6_mreq>().toUInt())
                    }
                    else -> -1
                }
                if (rc == 0) {
                    state.lock.withLock {
                        if (join) joinedMulticastGroups.add(multicastAddress)
                        else joinedMulticastGroups.remove(multicastAddress)
                    }
                    true
                } else {
                    state.emitError(IllegalStateException("Multicast ${if (join) "join" else "leave"} failed ($multicastAddress): ${strerror(errno)?.toKString() ?: errno}"))
                    false
                }
            } finally {
                freeaddrinfo(head)
            }
        }
    }

    override suspend fun send(bytes: ByteArray, host: String, port: Int): Int {
        if (isDisposed || !isWritable) return -1
        val targetHost = host.ifBlank { remoteHost ?: "" }
        val targetPort = if (port > 0) port else remotePort
        if (targetHost.isBlank() || targetPort <= 0 || targetPort > 65535) {
            state.emitError(IllegalArgumentException("Missing UDP destination host/port ($targetHost:$targetPort)"))
            return -1
        }
        if (!isBound) {
            val bindHost = if (targetHost.contains(':')) "::" else "0.0.0.0"
            if (!bind(bindHost, 0)) return -1
        }
        val fd = state.lock.withLock { socketFd }
        if (fd < 0) return -1

        val copy = bytes.copyOf()
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(ioQueue) {
                val sent = sendDatagramSync(fd, copy, targetHost, targetPort)
                continuation.resume(sent)
            }
        }
    }

    private fun sendDatagramSync(fd: Int, payload: ByteArray, targetHost: String, targetPort: Int): Int {
        memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
            hints.ai_family = state.lock.withLock { socketFamily }
            hints.ai_socktype = SOCK_DGRAM

            val resPtr = alloc<CPointerVar<addrinfo>>()
            var gai = getaddrinfo(targetHost, targetPort.toString(), hints.ptr, resPtr.ptr)
            if (gai != 0 && hints.ai_family != AF_UNSPEC) {
                hints.ai_family = AF_UNSPEC
                gai = getaddrinfo(targetHost, targetPort.toString(), hints.ptr, resPtr.ptr)
            }
            if (gai != 0 || resPtr.value == null) {
                state.emitError(IllegalStateException("UDP resolve failed for $targetHost:$targetPort"))
                return -1
            }

            val head = resPtr.value!!
            try {
                val info = head.pointed
                val sent = if (payload.isEmpty()) {
                    val dummy = ByteArray(1)
                    dummy.usePinned { pinned ->
                        sendto(fd, pinned.addressOf(0), 0uL, 0, info.ai_addr, info.ai_addrlen).toInt()
                    }
                } else {
                    val pfd = allocArray<pollfd>(1)
                    var n: Int
                    while (true) {
                        n = payload.usePinned { pinned ->
                            sendto(fd, pinned.addressOf(0), payload.size.toULong(), 0, info.ai_addr, info.ai_addrlen).toInt()
                        }
                        if (n >= 0) break
                        val err = errno
                        if (err == EINTR) continue
                        if (err == EAGAIN || err == EWOULDBLOCK) {
                            pfd[0].fd = fd
                            pfd[0].events = POLLOUT.toShort()
                            pfd[0].revents = 0
                            val pr = poll(pfd, 1u, 100)
                            if (pr > 0) continue
                        }
                        state.emitError(IllegalStateException("UDP sendto failed: ${strerror(err)?.toKString() ?: err}"))
                        return -1
                    }
                    n
                }
                if (sent >= 0) {
                    state.lock.withLock { outboundHistory.add(payload) }
                    state.recordWrite(sent)
                    state.emitWrite(ByteBuffer.wrap(payload, byteOrder = byteOrder))
                }
                return sent
            } finally {
                freeaddrinfo(head)
            }
        }
    }

    override suspend fun receive(maxBytes: Int, timeoutMs: Long): UdpDatagram? {
        if (maxBytes <= 0 || isPaused || isDisposed || state.isEnded) return null
        val fd = state.lock.withLock { if (boundState) socketFd else -1 }
        if (fd < 0) return null

        return suspendCancellableCoroutine { continuation ->
            val resumeLock = AppleReentrantLock()
            var resumed = false
            var cancelled = false

            fun finish(datagram: UdpDatagram?, err: Throwable? = null) {
                val canResume = resumeLock.withLock {
                    if (resumed) false else {
                        resumed = true
                        true
                    }
                }
                if (!canResume) return
                if (err != null) state.emitError(err)
                continuation.resume(datagram)
            }

            continuation.invokeOnCancellation {
                resumeLock.withLock { cancelled = true }
            }

            dispatch_async(ioQueue) {
                val bufCap = maxBytes.coerceIn(1, 65536)
                val rx = ByteArray(bufCap)
                val hasTimeout = timeoutMs > 0L
                val deadline = if (hasTimeout) TimeSource.Monotonic.markNow() + timeoutMs.milliseconds else null

                memScoped {
                    val pfd = allocArray<pollfd>(1)
                    val peerStorage = alloc<sockaddr_storage>()
                    val peerLen = alloc<UIntVar>()
                    while (!cancelled && !isDisposed && !isPaused) {
                        val activeFd = state.lock.withLock { socketFd }
                        if (activeFd != fd || activeFd < 0) {
                            finish(null)
                            return@dispatch_async
                        }

                        val sliceMs = if (deadline != null) {
                            val rem = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
                            if (rem <= 0L) {
                                finish(null)
                                return@dispatch_async
                            }
                            minOf(rem, 50L).toInt().coerceAtLeast(1)
                        } else {
                            50
                        }

                        pfd[0].fd = fd
                        pfd[0].events = POLLIN.toShort()
                        pfd[0].revents = 0
                        val pr = poll(pfd, 1u, sliceMs)
                        if (pr == 0) continue
                        if (pr < 0) {
                            if (errno == EINTR) continue
                            finish(null, IllegalStateException("UDP poll error: ${strerror(errno)?.toKString() ?: errno}"))
                            return@dispatch_async
                        }

                        peerLen.value = sizeOf<sockaddr_storage>().toUInt()
                        val n = rx.usePinned { pinned ->
                            recvfrom(fd, pinned.addressOf(0), bufCap.toULong(), 0, peerStorage.ptr.reinterpret(), peerLen.ptr).toInt()
                        }
                        if (n >= 0) {
                            val payload = if (n == 0) ByteArray(0) else rx.copyOf(n)
                            val sender = extractHostAndPort(peerStorage.ptr.reinterpret(), peerLen.value)
                            state.recordRead(n)
                            finish(UdpDatagram(payload, sender.first ?: "", sender.second))
                            return@dispatch_async
                        }
                        val err = errno
                        if (err != EAGAIN && err != EWOULDBLOCK && err != EINTR) {
                            finish(null, IllegalStateException("UDP recvfrom failed: ${strerror(err)?.toKString() ?: err}"))
                            return@dispatch_async
                        }
                    }
                    finish(null)
                }
            }
        }
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (maxBytes <= 0 || isPaused || isDisposed || state.isEnded) return null
        state.pollPushback(maxBytes)?.let { return it }
        val datagram = receive(maxBytes, timeoutMs = 0L) ?: return null
        return ByteBuffer.wrap(datagram.data, byteOrder = byteOrder)
    }

    override fun write(buffer: ByteBuffer): Boolean {
        val slice = buffer.remainingByteArray()
        return write(slice, 0, slice.size)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice offset=$offset length=$length size=${chunk.size}"
        }
        if (isDisposed || !isWritable) return false
        val host = remoteHost ?: return false
        val port = remotePort
        if (port <= 0) return false
        val fd = state.lock.withLock { socketFd }
        if (fd < 0) return false
        val slice = if (offset == 0 && length == chunk.size) chunk.copyOf() else chunk.copyOfRange(offset, offset + length)
        return sendDatagramSync(fd, slice, host, port) >= 0
    }

    override suspend fun flush() {
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
        closeSocketOnly()
        state.emitFinish()
        state.emitEnd()
        state.markDisposedAndNotifyClose()
    }

    override fun dispose() {
        closeSocketOnly()
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

    private fun closeSocketOnly() {
        val fdToClose = state.lock.withLock {
            val f = socketFd
            socketFd = -1
            boundState = false
            joinedMulticastGroups.clear()
            f
        }
        if (fdToClose >= 0) {
            platform.posix.close(fdToClose)
        }
    }

    private fun setSocketBooleanOption(fd: Int, level: Int, option: Int, enabled: Boolean) {
        memScoped {
            val flag = alloc<IntVar>()
            flag.value = if (enabled) 1 else 0
            setsockopt(fd, level, option, flag.ptr, sizeOf<IntVar>().toUInt())
        }
    }

    private fun setNonBlocking(fd: Int, nonBlocking: Boolean) {
        val currentFlags = fcntl(fd, F_GETFL, 0)
        if (currentFlags >= 0) {
            val updated = if (nonBlocking) (currentFlags or O_NONBLOCK) else (currentFlags and O_NONBLOCK.inv())
            fcntl(fd, F_SETFL, updated)
        }
    }

    private fun queryLocalEndpoint(fd: Int): Pair<String?, Int> {
        return memScoped {
            val storage = alloc<sockaddr_storage>()
            val len = alloc<UIntVar>()
            len.value = sizeOf<sockaddr_storage>().toUInt()
            if (getsockname(fd, storage.ptr.reinterpret(), len.ptr) != 0) return null to 0
            extractHostAndPort(storage.ptr.reinterpret(), len.value)
        }
    }

    private fun extractHostAndPort(sa: kotlinx.cinterop.CPointer<sockaddr>, saLen: UInt): Pair<String?, Int> {
        return memScoped {
            val hostBuf = allocArray<ByteVar>(128)
            val servBuf = allocArray<ByteVar>(16)
            val rc = getnameinfo(
                sa,
                saLen,
                hostBuf,
                128u,
                servBuf,
                16u,
                NI_NUMERICHOST or NI_NUMERICSERV
            )
            if (rc != 0) return null to 0
            val host = hostBuf.toKString()
            val port = servBuf.toKString().toIntOrNull() ?: 0
            host to port
        }
    }

    private fun resolveIpv4InterfaceAddress(interfaceName: String?): UInt {
        if (interfaceName.isNullOrBlank()) return INADDR_ANY
        return memScoped {
            val ifapVar = alloc<CPointerVar<ifaddrs>>()
            if (getifaddrs(ifapVar.ptr) != 0) return INADDR_ANY
            val head = ifapVar.value
            try {
                var cur = head
                while (cur != null) {
                    val iface = cur.pointed
                    val name = iface.ifa_name?.toKString()
                    val addr = iface.ifa_addr?.reinterpret<sockaddr>()
                    if (name == interfaceName && addr != null && addr.pointed.sa_family.toInt() == AF_INET) {
                        return addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                    }
                    cur = iface.ifa_next
                }
                // Fallback: try interpreting interfaceName as literal IPv4 address string.
                val hints = alloc<addrinfo>()
                memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
                hints.ai_family = AF_INET
                hints.ai_flags = AI_NUMERICHOST
                val resPtr = alloc<CPointerVar<addrinfo>>()
                if (getaddrinfo(interfaceName, null, hints.ptr, resPtr.ptr) == 0 && resPtr.value != null) {
                    val sAddr = resPtr.value!!.pointed.ai_addr!!.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                    freeaddrinfo(resPtr.value)
                    return sAddr
                }
                INADDR_ANY
            } finally {
                if (head != null) freeifaddrs(head)
            }
        }
    }
}
