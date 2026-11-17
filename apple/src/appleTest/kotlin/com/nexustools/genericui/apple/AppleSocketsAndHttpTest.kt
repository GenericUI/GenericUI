@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.net.http.HttpMethod
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close as posixClose
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleSocketsAndHttpTest {

    private fun startLoopbackTcpServer(
        onClient: (clientFd: Int) -> Unit
    ): Pair<Int, Int> {
        return memScoped {
            val serverFd = socket(AF_INET, SOCK_STREAM, 0)
            assertTrue(serverFd >= 0)
            val reuse = alloc<kotlinx.cinterop.IntVar>()
            reuse.value = 1
            setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<kotlinx.cinterop.IntVar>().toUInt())

            val addr = alloc<sockaddr_in>()
            memset(addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
            addr.sin_len = sizeOf<sockaddr_in>().toUByte()
            addr.sin_family = AF_INET.toUByte()
            addr.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1 in little-endian u32
            addr.sin_port = 0u
            assertEquals(0, bind(serverFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()))
            assertEquals(0, listen(serverFd, 4))

            val bound = alloc<sockaddr_in>()
            val boundLen = alloc<UIntVar>()
            boundLen.value = sizeOf<sockaddr_in>().toUInt()
            assertEquals(0, getsockname(serverFd, bound.ptr.reinterpret(), boundLen.ptr))
            val portRaw = bound.sin_port.toInt() and 0xFFFF
            val port = ((portRaw and 0xFF) shl 8) or ((portRaw ushr 8) and 0xFF)

            val readySem = dispatch_semaphore_create(0)
            dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)) {
                dispatch_semaphore_signal(readySem!!)
                val clientFd = accept(serverFd, null, null)
                if (clientFd >= 0) {
                    try {
                        onClient(clientFd)
                    } finally {
                        posixClose(clientFd)
                    }
                }
                posixClose(serverFd)
            }
            dispatch_semaphore_wait(readySem!!, DISPATCH_TIME_FOREVER)
            serverFd to port
        }
    }

    @Test
    fun tcpPlaintextLoopbackAndTlsClosedPort() {
        runBlocking {
        val (_, port) = startLoopbackTcpServer { clientFd ->
            val buf = ByteArray(128)
            val n = buf.usePinned { pinned ->
                recv(clientFd, pinned.addressOf(0), buf.size.toULong(), 0).toInt()
            }
            if (n > 0) {
                val reply = "ECHO:${buf.copyOf(n).decodeToString()}".encodeToByteArray()
                reply.usePinned { pinned ->
                    send(clientFd, pinned.addressOf(0), reply.size.toULong(), 0)
                }
            }
        }

        val tcp = AppleTcpSocketNativeImpl()
        try {
            assertTrue(tcp.connect("127.0.0.1", port, timeoutMs = 3000L, tls = false))
            assertTrue(tcp.isConnected)
            assertTrue(tcp.localPort > 0)
            assertTrue(tcp.write("ping-tcp".encodeToByteArray()))
            tcp.flush()

            val reply = tcp.read(128)
            assertNotNull(reply)
            assertEquals("ECHO:ping-tcp", reply.toByteArray().decodeToString())
            tcp.close()
        } finally {
            tcp.dispose()
        }

        // Verify TLS connect cleanly fails (and never hangs or fakes success) on an unreachable port.
        val tlsProbe = AppleTcpSocketNativeImpl()
        try {
            val ok = tlsProbe.connect("127.0.0.1", 1, timeoutMs = 600L, tls = true)
            assertFalse(ok)
            assertFalse(tlsProbe.isConnected)
        } finally {
            tlsProbe.dispose()
        }
        }
    }

    @Test
    fun udpLoopbackSendReceiveAndMulticastSupport() {
        runBlocking {
        val receiver = AppleUdpSocketNativeImpl()
        val sender = AppleUdpSocketNativeImpl()
        try {
            assertTrue(receiver.bind("127.0.0.1", 0))
            assertTrue(receiver.isBound)
            assertTrue(receiver.localPort > 0)
            assertTrue(receiver.supportsMulticast())

            assertTrue(sender.bind("127.0.0.1", 0))
            sender.broadcastEnabled = true
            assertTrue(sender.broadcastEnabled)

            val payload = "hello-apple-udp".encodeToByteArray()
            val sent = sender.send(payload, "127.0.0.1", receiver.localPort)
            assertEquals(payload.size, sent)

            val datagram = receiver.receive(1024, timeoutMs = 2000L)
            assertNotNull(datagram)
            assertEquals("hello-apple-udp", datagram.text)
            assertEquals("127.0.0.1", datagram.remoteAddress)
            assertEquals(sender.localPort, datagram.remotePort)

            val timedOut = receiver.receive(1024, timeoutMs = 60L)
            assertNull(timedOut)
        } finally {
            sender.dispose()
            receiver.dispose()
        }
        }
    }

    @Test
    fun httpClientRoutesLiveLoopbackAndErrorHandling() {
        runBlocking {
        val client = AppleHttpClientNativeImpl(allowLocalhostFallback = false)
        try {
            client.registerRoute("https://test.local/hello*") { req ->
                AppleHttpResponseNativeImpl(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("X-Echo-Method" to req.method.name),
                    initialBodyBytes = "route:${req.bodyText ?: ""}".encodeToByteArray(),
                    url = req.url
                )
            }

            val req = AppleHttpRequestNativeImpl("https://test.local/hello?x=1", method = HttpMethod.POST)
            req.writeText("payload")
            val routeResp = client.execute(req)
            assertEquals(200, routeResp.statusCode)
            assertEquals("POST", routeResp.header("x-echo-method"))
            assertEquals("route:payload", routeResp.bodyText)

            // Unreachable port must NEVER fabricate a fake 200 OK.
            val errResp = client.execute(AppleHttpRequestNativeImpl("http://127.0.0.1:1/fail", timeoutMs = 1000L))
            assertEquals(0, errResp.statusCode)
            assertFalse(errResp.isSuccessful)

            // Live NSURLSession round-trip against a real POSIX loopback HTTP server.
            val (_, httpPort) = startLoopbackTcpServer { clientFd ->
                val rx = ByteArray(1024)
                rx.usePinned { pinned ->
                    recv(clientFd, pinned.addressOf(0), rx.size.toULong(), 0)
                }
                val httpPayload = (
                    "HTTP/1.1 201 Created\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "X-Live-Apple: yes\r\n" +
                        "Content-Length: 7\r\n" +
                        "Connection: close\r\n\r\n" +
                        "OK-LIVE"
                    ).encodeToByteArray()
                httpPayload.usePinned { pinned ->
                    send(clientFd, pinned.addressOf(0), httpPayload.size.toULong(), 0)
                }
            }

            val conn = client.openConnection(
                AppleHttpRequestNativeImpl("http://127.0.0.1:$httpPort/live", timeoutMs = 4000L)
            )
            assertEquals(201, conn.statusCode)
            assertEquals("yes", conn.header("x-live-apple"))
            assertEquals("OK-LIVE", conn.bodyText)
            conn.close()
        } finally {
            client.dispose()
        }
        }
    }

    @Test
    fun uniformTypeHelperResolvesExtensionsMimeTypesAndFilters() {
        assertEquals("image/png", AppleUniformTypeHelper.mimeTypeForExtension("png"))
        assertEquals("image/png", AppleUniformTypeHelper.mimeTypeForPath("/tmp/icon.PNG"))
        assertEquals("image/svg+xml", AppleUniformTypeHelper.mimeTypeForExtension(".svg"))
        assertEquals("public.png", AppleUniformTypeHelper.identifierForExtension("png"))
        assertTrue(AppleUniformTypeHelper.conformsTo("png", "public.image"))
        assertNotNull(AppleUniformTypeHelper.extensionForMimeType("application/json"))

        val utTypes = AppleUniformTypeHelper.utTypesForFilters(
            listOf(FileTypeFilter("Images", extensions = listOf("png", "*.jpg"), mimeTypes = listOf("image/webp")))
        )
        assertTrue(utTypes.any { it.identifier == "public.png" })
        assertTrue(utTypes.any { it.identifier == "public.jpeg" })
    }

    @Test
    fun webSocketFailsCleanlyOnClosedLoopbackPort() {
        runBlocking {
        val ws = AppleWebSocketNativeImpl(connectTimeoutMs = 800L)
        try {
            var errorCalled = false
            ws.setOnError { errorCalled = true }
            val connected = ws.connect("ws://127.0.0.1:1/ws")
            assertFalse(connected)
            assertFalse(ws.isConnected)
            assertTrue(errorCalled)
        } finally {
            ws.dispose()
        }
        }
    }
}
