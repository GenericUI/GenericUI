package com.nexustools.genericui.jvm

import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmHttpUrlConnectionClientTest {

    @Test
    fun executesMockRoutesAndRealLocalHttpExchange(): Unit = runBlocking {
        val client = JvmHttpUrlConnectionClient()
        try {
            client.registerRoute("https://service.example.org/hello") { req ->
                HttpResponse(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("X-Mock" to "yes"),
                    bodyBytes = "hello:${req.method.name}".encodeToByteArray(),
                    url = req.url
                )
            }

            val mockConn = client.openConnection(
                HttpRequest(url = "https://service.example.org/hello", method = HttpMethod.GET)
            )
            assertIs<JvmHttpConnectionNativeImpl>(mockConn)
            assertIs<JvmHttpRequestNativeImpl>(mockConn.request)
            assertIs<JvmHttpResponseNativeImpl>(mockConn.response)
            assertEquals(200, mockConn.response.statusCode)
            assertEquals("yes", mockConn.response.header("x-mock"))
            assertEquals("hello:GET", mockConn.response.bodyText)
            mockConn.close()

            ServerSocket(0).use { server ->
                server.soTimeout = 5000
                val serverThread = thread(isDaemon = true) {
                    server.accept().use { sock ->
                        sock.soTimeout = 5000
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                        val requestLine = reader.readLine() ?: ""
                        var contentLen = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLen = line.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        val bodyChars = CharArray(contentLen)
                        var readTotal = 0
                        while (readTotal < contentLen) {
                            val n = reader.read(bodyChars, readTotal, contentLen - readTotal)
                            if (n < 0) break
                            readTotal += n
                        }
                        val bodyStr = String(bodyChars, 0, readTotal)
                        val respPayload = "$requestLine|$bodyStr"
                        val respBytes = respPayload.encodeToByteArray()
                        val rawResp = buildString {
                            append("HTTP/1.1 201 Created\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("X-Echo-Header: true\r\n")
                            append("Content-Length: ").append(respBytes.size).append("\r\n")
                            append("Connection: close\r\n\r\n")
                        }.encodeToByteArray() + respBytes
                        sock.getOutputStream().write(rawResp)
                        sock.getOutputStream().flush()
                    }
                }

                val realUrl = "http://127.0.0.1:${server.localPort}/submit"
                val realConn = client.openConnection(
                    HttpRequest(
                        url = realUrl,
                        method = HttpMethod.POST,
                        headers = mapOf("Content-Type" to "text/plain"),
                        bodyBytes = "payload-123".encodeToByteArray()
                    )
                )
                assertEquals(201, realConn.response.statusCode)
                assertEquals("true", realConn.response.header("X-Echo-Header"))
                assertTrue(realConn.response.bodyText.contains("POST /submit HTTP/1.1|payload-123"))
                assertEquals(2, client.requestCount)
                assertEquals(2, client.requestHistory().size)
                realConn.close()
                serverThread.join(2000L)
            }
        } finally {
            client.dispose()
        }
    }
}
