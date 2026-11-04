package com.nexustools.genericui.net.socketio

/**
 * Engine.IO v4 and Socket.IO v4 wire frame encoder and decoder.
 *
 * Engine.IO v4 packet types:
 * - `0`: OPEN (`0{"sid":"...","upgrades":[],"pingInterval":25000,"pingTimeout":20000}`)
 * - `1`: CLOSE
 * - `2`: PING (`2` -> client replies `3`)
 * - `3`: PONG (`3`)
 * - `4`: MESSAGE (carries a Socket.IO v4 packet payload)
 *
 * Socket.IO v4 packet types (prefixed with `4` when carried over Engine.IO):
 * - `40`: CONNECT (`40`, `40/ns,`, `40{"token":"..."}`)
 * - `41`: DISCONNECT (`41`, `41/ns,`)
 * - `42`: EVENT (`42["chat","hello"]`, `421["rpc","arg"]`, `42/ns,1["rpc","arg"]`)
 * - `43`: ACK (`431["ok"]`, `43/ns,1["ok"]`)
 * - `44`: CONNECT_ERROR (`44{"message":"Unauthorized"}`)
 */
object SocketIoParser {

    const val EIO_OPEN: Char = '0'
    const val EIO_CLOSE: Char = '1'
    const val EIO_PING: Char = '2'
    const val EIO_PONG: Char = '3'
    const val EIO_MESSAGE: Char = '4'
    const val EIO_UPGRADE: Char = '5'
    const val EIO_NOOP: Char = '6'

    fun isEngineOpen(rawFrame: String): Boolean =
        rawFrame.isNotEmpty() && rawFrame[0] == EIO_OPEN

    fun isEngineClose(rawFrame: String): Boolean =
        rawFrame.isNotEmpty() && rawFrame[0] == EIO_CLOSE

    fun isEnginePing(rawFrame: String): Boolean =
        rawFrame.isNotEmpty() && rawFrame[0] == EIO_PING

    fun isEnginePong(rawFrame: String): Boolean =
        rawFrame.isNotEmpty() && rawFrame[0] == EIO_PONG

    fun isEngineMessage(rawFrame: String): Boolean =
        rawFrame.isNotEmpty() && rawFrame[0] == EIO_MESSAGE

    fun encodePing(payload: String = ""): String = "$EIO_PING$payload"

    fun encodePong(pingFrame: String = "2"): String {
        val suffix = if (pingFrame.startsWith(EIO_PING)) pingFrame.substring(1) else ""
        return "$EIO_PONG$suffix"
    }

    fun encodeConnect(namespace: String = "/", auth: Map<String, String> = emptyMap()): String {
        val authArgs = if (auth.isEmpty()) emptyList() else listOf(encodeStringMapToJson(auth))
        return encodePacket(
            SocketIoPacket(
                type = SocketIoPacketType.CONNECT,
                namespace = normalizeNamespace(namespace),
                argsJson = authArgs
            )
        )
    }

    fun encodeDisconnect(namespace: String = "/"): String =
        encodePacket(
            SocketIoPacket(
                type = SocketIoPacketType.DISCONNECT,
                namespace = normalizeNamespace(namespace)
            )
        )

    fun encodeEvent(
        event: String,
        args: List<Any?> = emptyList(),
        namespace: String = "/",
        ackId: Int? = null
    ): String =
        encodePacket(
            SocketIoPacket(
                type = SocketIoPacketType.EVENT,
                namespace = normalizeNamespace(namespace),
                ackId = ackId,
                event = event,
                argsJson = args.map { anyToJsonToken(it) }
            )
        )

    fun encodeAck(
        ackId: Int,
        args: List<Any?> = emptyList(),
        namespace: String = "/"
    ): String =
        encodePacket(
            SocketIoPacket(
                type = SocketIoPacketType.ACK,
                namespace = normalizeNamespace(namespace),
                ackId = ackId,
                argsJson = args.map { anyToJsonToken(it) }
            )
        )

    fun encodePacket(packet: SocketIoPacket, includeEnginePrefix: Boolean = true): String {
        val sb = StringBuilder()
        if (includeEnginePrefix) {
            sb.append(EIO_MESSAGE)
        }
        sb.append(packet.type.code)

        if (packet.type == SocketIoPacketType.BINARY_EVENT || packet.type == SocketIoPacketType.BINARY_ACK) {
            sb.append(packet.binaryPayloads.size)
            sb.append('-')
        }

        val ns = normalizeNamespace(packet.namespace)
        val hasCustomNamespace = ns != "/"
        val hasPayloadOrAck = packet.ackId != null ||
            !packet.event.isNullOrEmpty() ||
            packet.argsJson.isNotEmpty() ||
            packet.type == SocketIoPacketType.EVENT ||
            packet.type == SocketIoPacketType.ACK

        if (hasCustomNamespace) {
            sb.append(ns)
            sb.append(',')
        }

        if (packet.ackId != null) {
            sb.append(packet.ackId)
        }

        when (packet.type) {
            SocketIoPacketType.CONNECT,
            SocketIoPacketType.CONNECT_ERROR -> {
                if (packet.argsJson.isNotEmpty()) {
                    sb.append(packet.argsJson.first())
                }
            }
            SocketIoPacketType.DISCONNECT -> {
                // No body payload for DISCONNECT
            }
            SocketIoPacketType.EVENT,
            SocketIoPacketType.BINARY_EVENT -> {
                sb.append('[')
                sb.append('"').append(escapeJson(packet.event ?: "")).append('"')
                for (arg in packet.argsJson) {
                    sb.append(',')
                    sb.append(formatJsonArgForWire(arg))
                }
                sb.append(']')
            }
            SocketIoPacketType.ACK,
            SocketIoPacketType.BINARY_ACK -> {
                sb.append('[')
                for ((index, arg) in packet.argsJson.withIndex()) {
                    if (index > 0) sb.append(',')
                    sb.append(formatJsonArgForWire(arg))
                }
                sb.append(']')
            }
        }
        return sb.toString()
    }

    fun decodePacket(rawFrame: String): SocketIoPacket? {
        val trimmed = rawFrame.trim()
        if (trimmed.isEmpty()) return null

        val sioPayload = if (trimmed[0] == EIO_MESSAGE && trimmed.length >= 2 && trimmed[1] in '0'..'6') {
            trimmed.substring(1)
        } else if (trimmed[0] in '0'..'6' && (trimmed.length == 1 || trimmed[0] != EIO_OPEN || !trimmed.startsWith("0{\"sid\""))) {
            trimmed
        } else {
            return null
        }

        if (sioPayload.isEmpty()) return null
        val typeCode = sioPayload[0].digitToIntOrNull() ?: return null
        val packetType = SocketIoPacketType.fromCode(typeCode) ?: return null

        var cursor = 1

        if (packetType == SocketIoPacketType.BINARY_EVENT || packetType == SocketIoPacketType.BINARY_ACK) {
            while (cursor < sioPayload.length && sioPayload[cursor].isDigit()) {
                cursor++
            }
            if (cursor < sioPayload.length && sioPayload[cursor] == '-') {
                cursor++
            }
        }

        var namespace = "/"
        if (cursor < sioPayload.length && sioPayload[cursor] == '/') {
            val nsStart = cursor
            while (cursor < sioPayload.length &&
                sioPayload[cursor] != ',' &&
                sioPayload[cursor] != '[' &&
                sioPayload[cursor] != '{'
            ) {
                cursor++
            }
            namespace = sioPayload.substring(nsStart, cursor)
            if (cursor < sioPayload.length && sioPayload[cursor] == ',') {
                cursor++
            }
        }

        var ackId: Int? = null
        if (cursor < sioPayload.length && sioPayload[cursor].isDigit()) {
            val idStart = cursor
            while (cursor < sioPayload.length && sioPayload[cursor].isDigit()) {
                cursor++
            }
            ackId = sioPayload.substring(idStart, cursor).toIntOrNull()
        }

        val rest = if (cursor < sioPayload.length) sioPayload.substring(cursor).trim() else ""

        return when (packetType) {
            SocketIoPacketType.CONNECT,
            SocketIoPacketType.CONNECT_ERROR -> {
                val args = if (rest.isNotEmpty()) listOf(rest) else emptyList()
                SocketIoPacket(
                    type = packetType,
                    namespace = namespace,
                    ackId = ackId,
                    argsJson = args
                )
            }
            SocketIoPacketType.DISCONNECT -> {
                SocketIoPacket(
                    type = packetType,
                    namespace = namespace,
                    ackId = ackId
                )
            }
            SocketIoPacketType.EVENT,
            SocketIoPacketType.BINARY_EVENT -> {
                val elements = parseTopLevelJsonArray(rest)
                val eventName = elements.firstOrNull()?.let { unquoteJsonToken(it) } ?: ""
                val args = if (elements.size > 1) {
                    elements.drop(1).map { unquoteJsonToken(it) }
                } else {
                    emptyList()
                }
                SocketIoPacket(
                    type = packetType,
                    namespace = namespace,
                    ackId = ackId,
                    event = eventName,
                    argsJson = args
                )
            }
            SocketIoPacketType.ACK,
            SocketIoPacketType.BINARY_ACK -> {
                val elements = parseTopLevelJsonArray(rest).map { unquoteJsonToken(it) }
                SocketIoPacket(
                    type = packetType,
                    namespace = namespace,
                    ackId = ackId,
                    argsJson = elements
                )
            }
        }
    }

    fun extractSidFromJson(json: String): String? {
        val keyIndex = json.indexOf("\"sid\"")
        if (keyIndex < 0) return null
        val colonIndex = json.indexOf(':', keyIndex + 5)
        if (colonIndex < 0) return null
        val firstQuote = json.indexOf('"', colonIndex + 1)
        if (firstQuote < 0) return null
        val secondQuote = json.indexOf('"', firstQuote + 1)
        if (secondQuote < 0) return null
        return json.substring(firstQuote + 1, secondQuote)
    }

    internal fun normalizeNamespace(namespace: String): String {
        val trimmed = namespace.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    internal fun anyToJsonToken(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is String -> value
        is Map<*, *> -> {
            val entries = value.entries.joinToString(",") { (k, v) ->
                "\"${escapeJson(k.toString())}\":${formatValueAsExplicitJson(v)}"
            }
            "{$entries}"
        }
        is Iterable<*> -> {
            val items = value.joinToString(",") { formatValueAsExplicitJson(it) }
            "[$items]"
        }
        else -> value.toString()
    }

    private fun formatValueAsExplicitJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is String -> "\"${escapeJson(value)}\""
        else -> anyToJsonToken(value)
    }

    private fun formatJsonArgForWire(token: String): String {
        val t = token.trim()
        if (t.isEmpty()) return "\"\""
        if (t == "null" || t == "true" || t == "false") return t
        if (t.toLongOrNull() != null || t.toDoubleOrNull() != null) return t
        if ((t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) ||
            (t.startsWith("{") && t.endsWith("}")) ||
            (t.startsWith("[") && t.endsWith("]"))
        ) {
            return t
        }
        return "\"${escapeJson(token)}\""
    }

    private fun encodeStringMapToJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        val body = map.entries.joinToString(",") { (k, v) ->
            "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
        }
        return "{$body}"
    }

    private fun parseTopLevelJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false
        var braceDepth = 0
        var bracketDepth = 0

        for (ch in inner) {
            if (escaped) {
                current.append(ch)
                escaped = false
                continue
            }
            if (ch == '\\' && inQuotes) {
                current.append(ch)
                escaped = true
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
                current.append(ch)
                continue
            }
            if (!inQuotes) {
                when (ch) {
                    '{' -> braceDepth++
                    '}' -> if (braceDepth > 0) braceDepth--
                    '[' -> bracketDepth++
                    ']' -> if (bracketDepth > 0) bracketDepth--
                    ',' -> if (braceDepth == 0 && bracketDepth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                        continue
                    }
                }
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    private fun unquoteJsonToken(token: String): String {
        val t = token.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            val raw = t.substring(1, t.length - 1)
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    val next = raw[i + 1]
                    when (next) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        else -> sb.append(next)
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
        return t
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
