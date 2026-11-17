package com.nexustools.genericui.jvm

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class JvmRfc6455WebSocketNativeImplTest {

    @Test
    fun exchangesRfc6455HandshakeTextBinaryAndCloseFramesOverRealSocket(): Unit = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val serverDone = CountDownLatch(1)
            var serverError: Throwable? = null

            thread(isDaemon = true) {
                try {
                    server.accept().use { sock ->
                        sock.soTimeout = 5000
                        val inp = sock.getInputStream()
                        val out = sock.getOutputStream()

                        var secKey = ""
                        while (true) {
                            val line = readLine(inp) ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                                secKey = line.substringAfter(':').trim()
                            }
                        }
                        val acceptHash = MessageDigest.getInstance("SHA-1")
                            .digest((secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encodeToByteArray())
                        val acceptKey = Base64.encode(acceptHash)
                        val upgradeResp = buildString {
                            append("HTTP/1.1 101 Switching Protocols\r\n")
                            append("Upgrade: websocket\r\n")
                            append("Connection: Upgrade\r\n")
                            append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n\r\n")
                        }
                        out.write(upgradeResp.encodeToByteArray())
                        out.flush()

                        // 1. Read masked client text frame and echo back unmasked fragmented text frames
                        val (op1, payload1) = readClientMaskedFrame(inp)
                        assertEquals(0x1, op1)
                        val text = payload1.decodeToString()
                        val half = text.length / 2
                        writeServerFrame(out, fin = false, opcode = 0x1, payload = text.substring(0, half).encodeToByteArray())
                        writeServerFrame(out, fin = true, opcode = 0x0, payload = text.substring(half).encodeToByteArray())

                        // 2. Read masked client binary frame and echo back unmasked binary frame
                        val (op2, payload2) = readClientMaskedFrame(inp)
                        assertEquals(0x2, op2)
                        writeServerFrame(out, fin = true, opcode = 0x2, payload = payload2)

                        // 3. Read close frame
                        val (op3, _) = readClientMaskedFrame(inp)
                        assertEquals(0x8, op3)
                    }
                } catch (t: Throwable) {
                    serverError = t
                } finally {
                    serverDone.countDown()
                }
            }

            val ws = JvmRfc6455WebSocketNativeImpl()
            val openedLatch = CountDownLatch(1)
            val textLatch = CountDownLatch(1)
            val binaryLatch = CountDownLatch(1)
            val closedLatch = CountDownLatch(1)
            var receivedText: String? = null
            var receivedBinary: ByteArray? = null
            var closeCode = -1

            ws.setOnOpen { openedLatch.countDown() }
            ws.setOnTextMessage {
                receivedText = it
                textLatch.countDown()
            }
            ws.setOnBinaryMessage {
                receivedBinary = it
                binaryLatch.countDown()
            }
            ws.setOnClose { code, _ ->
                closeCode = code
                closedLatch.countDown()
            }

            try {
                assertTrue(ws.connect("ws://127.0.0.1:${server.localPort}/chat"))
                assertTrue(openedLatch.await(3, TimeUnit.SECONDS))
                assertTrue(ws.isConnected)

                assertTrue(ws.sendText("hello-rfc6455"))
                assertTrue(textLatch.await(3, TimeUnit.SECONDS))
                assertEquals("hello-rfc6455", receivedText)

                val binData = byteArrayOf(10, 20, 30, 40)
                assertTrue(ws.sendBinary(binData))
                assertTrue(binaryLatch.await(3, TimeUnit.SECONDS))
                assertContentEquals(binData, receivedBinary)

                ws.close(1000, "Done")
                assertTrue(closedLatch.await(3, TimeUnit.SECONDS))
                assertFalse(ws.isConnected)
                assertEquals(1000, closeCode)
                assertTrue(serverDone.await(3, TimeUnit.SECONDS))
                serverError?.let { throw it }
            } finally {
                ws.dispose()
            }
        }
        Unit
    }

    private fun readLine(inp: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = inp.read()
            if (b < 0) return if (out.size() == 0) null else out.toByteArray().decodeToString().trimEnd('\r')
            if (b == '\n'.code) return out.toByteArray().decodeToString().trimEnd('\r')
            out.write(b)
        }
    }

    private fun readClientMaskedFrame(inp: InputStream): Pair<Int, ByteArray> {
        val b0 = inp.read()
        val b1 = inp.read()
        if (b0 < 0 || b1 < 0) throw EOFException()
        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        var len = b1 and 0x7F
        if (len == 126) {
            len = ((inp.read() shl 8) or inp.read())
        }
        val mask = if (masked) ByteArray(4).also { readFully(inp, it) } else null
        val payload = ByteArray(len)
        readFully(inp, payload)
        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
            }
        }
        return opcode to payload
    }

    private fun writeServerFrame(out: OutputStream, fin: Boolean, opcode: Int, payload: ByteArray) {
        out.write((if (fin) 0x80 else 0x00) or (opcode and 0x0F))
        out.write(payload.size)
        out.write(payload)
        out.flush()
    }

    private fun readFully(inp: InputStream, dst: ByteArray) {
        var off = 0
        while (off < dst.size) {
            val n = inp.read(dst, off, dst.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }
}
