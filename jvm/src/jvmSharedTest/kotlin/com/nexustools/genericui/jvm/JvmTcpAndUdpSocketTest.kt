package com.nexustools.genericui.jvm

import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmTcpAndUdpSocketTest {

    @AfterTest
    fun tearDown() {
        JvmTcpSocketNativeImpl.clearLoopback()
        JvmUdpSocketNativeImpl.clearLoopback()
    }

    @Test
    fun tcpSocketCommunicatesOverRealLoopbackSocketAndMockLoopback(): Unit = runBlocking {
        // 1. Real java.net.ServerSocket exchange over 127.0.0.1
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val serverThread = thread(isDaemon = true) {
                server.accept().use { client ->
                    client.soTimeout = 5000
                    val inp = client.getInputStream()
                    val out = client.getOutputStream()
                    val buf = ByteArray(64)
                    val n = inp.read(buf)
                    if (n > 0) {
                        val req = buf.copyOf(n).decodeToString()
                        out.write("ECHO:$req".encodeToByteArray())
                        out.flush()
                    }
                }
            }

            val tcp = JvmTcpSocketNativeImpl()
            try {
                assertTrue(tcp.connect("127.0.0.1", server.localPort, timeoutMs = 2000L, tls = false))
                assertTrue(tcp.isConnected)
                assertTrue(tcp.localPort > 0)
                val payload = "ping-real".encodeToByteArray()
                assertTrue(tcp.write(payload, 0, payload.size))
                val reply = tcp.read(1024)
                assertNotNull(reply)
                assertEquals("ECHO:ping-real", reply.toString("utf8"))
            } finally {
                tcp.dispose()
                serverThread.join(2000L)
            }
        }

        // 2. Registered in-memory loopback server exchange
        DefaultTcpSocketNativeImpl.registerLoopbackServer(19555) { req ->
            ("MOCK:" + req.decodeToString()).encodeToByteArray()
        }
        val mockTcp = JvmTcpSocketNativeImpl()
        try {
            assertTrue(mockTcp.connect("127.0.0.1", 19555))
            val bytes = "hello".encodeToByteArray()
            assertTrue(mockTcp.write(bytes, 0, bytes.size))
            assertEquals("MOCK:hello", mockTcp.read(1024)?.toString("utf8"))
        } finally {
            mockTcp.dispose()
        }

        // 3. Permissive trust manager contract check
        val tm = JvmPermissiveX509TrustManager()
        tm.checkClientTrusted(null, "RSA")
        tm.checkServerTrusted(null, "RSA")
        assertEquals(0, tm.acceptedIssuers.size)
    }

    @Test
    fun udpSocketBindsSendsReceivesAndTogglesMulticastGroup(): Unit = runBlocking {
        val receiver = JvmUdpSocketNativeImpl()
        val sender = JvmUdpSocketNativeImpl()
        try {
            assertTrue(receiver.bind("127.0.0.1", 0))
            assertTrue(sender.bind("127.0.0.1", 0))
            assertTrue(receiver.isBound)
            assertTrue(receiver.localPort > 0)

            val payload = "udp-jvm-hello".encodeToByteArray()
            assertEquals(payload.size, sender.send(payload, "127.0.0.1", receiver.localPort))

            val datagram = receiver.receive(1024, 1500L)
            assertNotNull(datagram)
            assertEquals("udp-jvm-hello", datagram.text)

            // Verify multicast join/leave lifecycle executes cleanly
            val joined = receiver.joinMulticastGroup("239.255.0.2")
            assertTrue(joined)
            assertTrue(receiver.leaveMulticastGroup("239.255.0.2"))
        } finally {
            sender.dispose()
            receiver.dispose()
        }
    }
}
