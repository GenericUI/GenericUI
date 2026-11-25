package com.nexustools.genericui.net

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Stream
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultWebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.net.http.HttpClient
import com.nexustools.genericui.net.http.HttpConnection
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse
import com.nexustools.genericui.net.socketio.SocketIoClient
import com.nexustools.genericui.net.socketio.SocketIoOptions
import com.nexustools.genericui.net.socketio.SocketIoPacketType
import com.nexustools.genericui.net.socketio.SocketIoParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkHttpSocketIoTest {

    private class FakeLoopbackSocketFactory : NativeFactory {
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Unused in NetworkHttpSocketIoTest")
        override val backendTarget: BackendTarget = BackendTarget.Swing
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = unsupported()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unsupported()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unsupported()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = unsupported()
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(
            classPrefix: String,
            tagName: String
        ): com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unsupported()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unsupported()
        override fun createNavbar(): NavbarNativeImpl<*> = unsupported()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unsupported()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unsupported()
        override fun createSpinner(): SpinnerNativeImpl<*> = unsupported()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = unsupported()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = unsupported()
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true
        override fun supportsMulticastUdp(): Boolean = true
    }

    @AfterTest
    fun tearDown() {
        DefaultTcpSocketNativeImpl.clearLoopbackServers()
        DefaultUdpSocketNativeImpl.clearLoopbackEndpoints()
        DefaultHttpClientNativeImpl.clearStaticRoutes()
    }

    @Test
    fun testNetworkSupportsTcpAndUdp() {
        val savedFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = null
            assertTrue(Network.supportsTcp())
            assertTrue(Network.supportsUdp())
            assertTrue(Network.supportsMulticastUdp())
        } finally {
            GenericUI.activeFactory = savedFactory
        }
    }

    @Test
    fun testTcpSocketStreamLoopbackAndServerHandler() = runSuspendTest {
        val savedFactory = GenericUI.activeFactory
        GenericUI.initialize(FakeLoopbackSocketFactory())
        try {
            val echoSocket = Network.openTcpSocket("127.0.0.1", 9001)
            assertIs<Stream>(echoSocket)
            assertTrue(echoSocket.isConnected)
            assertEquals("127.0.0.1", echoSocket.remoteHost)
            assertEquals(9001, echoSocket.remotePort)
            assertTrue(echoSocket.localPort > 0)

            assertTrue(echoSocket.writeText("hello-tcp"))
            echoSocket.flush()
            assertEquals("hello-tcp", echoSocket.readText())

            val outBuf = ByteBuffer.allocate(8).putIntBE(0x12345678).putIntBE(0x0ABCDEF0).flip()
            assertTrue(echoSocket.write(outBuf))
            echoSocket.flush()
            val inBuf = echoSocket.readBuffer()
            assertNotNull(inBuf)
            assertEquals(0x12345678, inBuf.getIntBE())
            assertEquals(0x0ABCDEF0, inBuf.getIntBE())
            echoSocket.close()
            assertFalse(echoSocket.isConnected)
            echoSocket.dispose()

            DefaultTcpSocketNativeImpl.registerLoopbackServer(
                port = 9002,
                greeting = "WELCOME\n".encodeToByteArray()
            ) { requestBytes ->
                val cmd = ByteBuffer.wrap(requestBytes).toString().trim()
                "REPLY:$cmd".encodeToByteArray()
            }

            val rpcSocket = Network.openTcpSocket()
            assertTrue(rpcSocket.connect("localhost", 9002))
            assertEquals("WELCOME\n", rpcSocket.readText())
            assertTrue(rpcSocket.writeLine("STATUS"))
            rpcSocket.flush()
            assertEquals("REPLY:STATUS", rpcSocket.readText())
            rpcSocket.dispose()
        } finally {
            GenericUI.activeFactory = savedFactory
        }
    }

    @Test
    fun testUdpSocketStreamLoopbackAndMulticast() = runSuspendTest {
        val savedFactory = GenericUI.activeFactory
        GenericUI.initialize(FakeLoopbackSocketFactory())
        try {
            val receiver = Network.openUdpSocket("127.0.0.1", 0)
            val sender = Network.openUdpSocket("127.0.0.1", 0)
            assertIs<Stream>(receiver)
            assertIs<Stream>(sender)
            assertTrue(receiver.isBound)
            assertTrue(receiver.localPort > 0)

            val sentBytes = sender.sendText("udp-ping", "127.0.0.1", receiver.localPort)
            assertEquals(8, sentBytes)

            val datagram = receiver.receive()
            assertNotNull(datagram)
            assertEquals("udp-ping", datagram.text)
            assertEquals("127.0.0.1", datagram.remoteAddress)
            assertEquals(sender.localPort, datagram.remotePort)

            val binaryPayload = ByteBuffer.allocate(4).putIntBE(424242).flip()
            assertEquals(4, sender.send(binaryPayload, "127.0.0.1", receiver.localPort))
            val binaryDatagram = receiver.receive()
            assertNotNull(binaryDatagram)
            assertEquals(424242, binaryDatagram.buffer.getIntBE())

            assertTrue(receiver.joinMulticastGroup("239.255.0.1"))
            sender.sendText("multicast-hello", "239.255.0.1", receiver.localPort)
            val multicastDatagram = receiver.receive()
            assertNotNull(multicastDatagram)
            assertEquals("multicast-hello", multicastDatagram.text)
            assertTrue(receiver.leaveMulticastGroup("239.255.0.1"))

            sender.close()
            receiver.close()
            sender.dispose()
            receiver.dispose()
        } finally {
            GenericUI.activeFactory = savedFactory
        }
    }

    @Test
    fun testHttpClientAndHttpConnectionStreams() = runSuspendTest {
        val mockImpl = DefaultHttpClientNativeImpl()
        mockImpl.registerRoute("https://api.example.com/items") { req ->
            when (req.method) {
                HttpMethod.GET -> HttpResponse(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("Content-Type" to "application/json", "X-Custom" to "yes"),
                    bodyBytes = """["item-1","item-2"]""".encodeToByteArray(),
                    url = req.url
                )
                HttpMethod.POST -> HttpResponse(
                    statusCode = 201,
                    statusText = "Created",
                    headers = mapOf("Content-Type" to "application/json"),
                    bodyBytes = """{"created":${req.bodyText}}""".encodeToByteArray(),
                    url = req.url
                )
                else -> HttpResponse(statusCode = 405, statusText = "Method Not Allowed", url = req.url)
            }
        }
        mockImpl.registerRoute("https://api.example.com/binary") { req ->
            val buf = ByteBuffer.allocate(8).putIntBE(100).putIntBE(200)
            HttpResponse(
                statusCode = 200,
                statusText = "OK",
                headers = mapOf("Content-Type" to "application/octet-stream"),
                bodyBytes = buf.toByteArray(),
                url = req.url
            )
        }

        val client = HttpClient(
            nativeImpl = mockImpl,
            defaultHeaders = mapOf("Accept" to "application/json"),
            defaultTimeoutMs = 30_000L,
            followRedirects = true
        )

        val getConn = client.get("https://api.example.com/items")
        assertIs<HttpConnection>(getConn)
        assertIs<Stream>(getConn)
        assertIs<Writable>(getConn.request)
        assertIs<Readable>(getConn.response)
        assertTrue(getConn.isSuccessful)
        assertEquals(200, getConn.statusCode)
        assertEquals("yes", getConn.header("x-custom"))
        assertEquals("""["item-1","item-2"]""", getConn.bodyText)

        val postConn = client.postText("https://api.example.com/items", """{"name":"widget"}""")
        assertEquals(201, postConn.statusCode)
        assertEquals("""{"created":{"name":"widget"}}""", postConn.bodyText)

        val binBuf = client.getBuffer("https://api.example.com/binary")
        assertEquals(8, binBuf.remaining)
        assertEquals(100, binBuf.getIntBE())
        assertEquals(200, binBuf.getIntBE())

        val streamConn = client.get("https://api.example.com/items")
        val chunk = streamConn.response.read()
        assertNotNull(chunk)
        assertEquals("""["item-1","item-2"]""", chunk.toString())
        streamConn.close()

        assertEquals("application/json", getConn.request.header("Accept"))
        client.dispose()
    }

    @Test
    fun testSocketIoParserEncodingAndDecoding() {
        assertEquals("40", SocketIoParser.encodeConnect("/"))
        assertEquals("40/admin,", SocketIoParser.encodeConnect("/admin"))
        assertEquals("""40/admin,{"token":"secret"}""", SocketIoParser.encodeConnect("/admin", mapOf("token" to "secret")))
        assertEquals("3", SocketIoParser.encodePong("2"))
        assertEquals("3probe", SocketIoParser.encodePong("2probe"))

        val eventFrame = SocketIoParser.encodeEvent("chat", listOf("hello"))
        assertEquals("""42["chat","hello"]""", eventFrame)

        val decodedEvent = SocketIoParser.decodePacket(eventFrame)
        assertNotNull(decodedEvent)
        assertEquals(SocketIoPacketType.EVENT, decodedEvent.type)
        assertEquals("/", decodedEvent.namespace)
        assertNull(decodedEvent.ackId)
        assertEquals("chat", decodedEvent.event)
        assertEquals(listOf("hello"), decodedEvent.argsJson)

        val decodedAck = SocketIoParser.decodePacket("""431["ok"]""")
        assertNotNull(decodedAck)
        assertEquals(SocketIoPacketType.ACK, decodedAck.type)
        assertEquals("/", decodedAck.namespace)
        assertEquals(1, decodedAck.ackId)
        assertEquals(listOf("ok"), decodedAck.argsJson)

        val customNsEvent = SocketIoParser.encodeEvent("update", listOf("v1", 42), namespace = "/metrics", ackId = 7)
        assertEquals("""42/metrics,7["update","v1",42]""", customNsEvent)
        val decodedCustom = SocketIoParser.decodePacket(customNsEvent)
        assertNotNull(decodedCustom)
        assertEquals(SocketIoPacketType.EVENT, decodedCustom.type)
        assertEquals("/metrics", decodedCustom.namespace)
        assertEquals(7, decodedCustom.ackId)
        assertEquals("update", decodedCustom.event)
        assertEquals(listOf("v1", "42"), decodedCustom.argsJson)
    }

    @Test
    fun testSocketIoClientHandshakePingPongEventsAndAcks() = runSuspendTest {
        val ws = DefaultWebSocketNativeImpl()
        val client = SocketIoClient(
            url = "http://localhost:3000",
            options = SocketIoOptions(query = mapOf("room" to "general")),
            webSocketNativeImpl = ws,
            httpClient = HttpClient()
        )

        var connectFired = 0
        var disconnectReason: String? = null
        val receivedChatArgs = ArrayList<List<String>>()
        var onceCount = 0

        client.onConnect { connectFired++ }
        client.onDisconnect { reason -> disconnectReason = reason }
        client.on("chat") { args -> receivedChatArgs.add(args) }
        client.once("welcome") { onceCount++ }

        assertTrue(client.connect())
        assertTrue(client.isConnected)
        assertEquals(1, connectFired)
        assertEquals("mock-eio-sid-1", client.engineSocketId)
        assertEquals("mock-sio-sid-1", client.socketId)
        assertTrue(ws.sentTextHistory().contains("40"))
        assertEquals("ws://localhost:3000/socket.io/?EIO=4&transport=websocket&room=general", ws.url)

        ws.simulateServerText("2")
        assertTrue(ws.sentTextHistory().contains("3"))

        assertTrue(client.emit("chat", "hello"))
        assertTrue(ws.sentTextHistory().contains("""42["chat","hello"]"""))

        ws.simulateServerText("""42["chat","hello from server"]""")
        assertEquals(1, receivedChatArgs.size)
        assertEquals(listOf("hello from server"), receivedChatArgs.first())

        ws.simulateServerText("""42["welcome","first"]""")
        ws.simulateServerText("""42["welcome","second"]""")
        assertEquals(1, onceCount)

        val ackResult = client.emitWithAck("rpc", "ping")
        assertNotNull(ackResult)
        assertContentEquals(listOf("ok"), ackResult)
        assertTrue(ws.sentTextHistory().contains("""421["rpc","ping"]"""))

        client.disconnect()
        assertFalse(client.isConnected)
        assertTrue(ws.sentTextHistory().contains("41"))
        assertEquals("io client disconnect", disconnectReason)
        client.dispose()
    }

    private fun runSuspendTest(block: suspend () -> Unit) {
        var failure: Throwable? = null
        var completed = false
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                completed = true
                failure = result.exceptionOrNull()
            }
        )
        failure?.let { throw it }
        assertTrue(completed, "Expected coroutine test block to complete synchronously")
    }
}
