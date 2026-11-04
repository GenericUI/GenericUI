package com.nexustools.genericui.net.socketio

/**
 * Configuration options for [SocketIoClient].
 */
data class SocketIoOptions(
    val path: String = "/socket.io/",
    val namespace: String = "/",
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val auth: Map<String, String> = emptyMap(),
    val reconnection: Boolean = true,
    val reconnectionAttempts: Int = Int.MAX_VALUE,
    val reconnectionDelayMs: Long = 1_000L,
    val reconnectionDelayMaxMs: Long = 5_000L,
    val timeoutMs: Long = 20_000L,
    val transports: List<String> = listOf("websocket", "polling")
)
