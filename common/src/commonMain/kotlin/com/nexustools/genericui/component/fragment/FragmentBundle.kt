package com.nexustools.genericui.component.fragment

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Key-value state bundle for saving and restoring [Fragment] instance state (`onSaveInstanceState` / `onCreate`).
 * Directly mirrors Android's `Bundle` across all 8 backends.
 */
class FragmentBundle {
    private val map = linkedMapOf<String, Any?>()

    fun putString(key: String, value: String?) { map[key] = value }
    fun getString(key: String, defaultValue: String? = null): String? = map[key] as? String ?: defaultValue

    fun putInt(key: String, value: Int) { map[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = map[key] as? Int ?: defaultValue

    fun putLong(key: String, value: Long) { map[key] = value }
    fun getLong(key: String, defaultValue: Long = 0L): Long = map[key] as? Long ?: defaultValue

    fun putFloat(key: String, value: Float) { map[key] = value }
    fun getFloat(key: String, defaultValue: Float = 0f): Float = map[key] as? Float ?: defaultValue

    fun putDouble(key: String, value: Double) { map[key] = value }
    fun getDouble(key: String, defaultValue: Double = 0.0): Double = map[key] as? Double ?: defaultValue

    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = map[key] as? Boolean ?: defaultValue

    fun putStringList(key: String, value: List<String>?) { map[key] = value?.toList() }
    fun getStringList(key: String, defaultValue: List<String> = emptyList()): List<String> {
        val raw = map[key] ?: return defaultValue
        return if (raw is List<*>) raw.filterIsInstance<String>() else defaultValue
    }

    fun containsKey(key: String): Boolean = map.containsKey(key)
    fun keys(): Set<String> = map.keys.toSet()
    fun clear() = map.clear()

    /**
     * Stores an arbitrary [value] under [key]. Values of type `String`, `Int`, `Long`, `Float`, `Double`,
     * `Boolean`, and `List<String>` remain readable through the typed getters ([getString], [getInt], ...);
     * any other type is stored as-is and readable via [get].
     */
    fun put(key: String, value: Any?) { map[key] = value }

    /** Returns the raw value stored under [key], or `null` if absent. */
    operator fun get(key: String): Any? = map[key]

    /** Copies every entry of [other] into this bundle (overwriting existing keys). */
    fun putAll(other: FragmentBundle) {
        map.putAll(other.map)
    }

    /** Returns a read-only snapshot of all entries in this bundle. */
    fun toMap(): Map<String, Any?> = LinkedHashMap(map)

    /** Returns `true` if command-line arguments have been stored under [KEY_COMMAND_LINE_ARGS]. */
    fun hasCommandLineArgs(): Boolean = containsKey(KEY_COMMAND_LINE_ARGS)

    /**
     * Returns the command-line arguments stored under [KEY_COMMAND_LINE_ARGS] as a [List] of [String]s,
     * or [emptyList] if none were provided.
     */
    fun getCommandLineArgs(): List<String> = when (val raw = map[KEY_COMMAND_LINE_ARGS]) {
        is List<*> -> raw.filterIsInstance<String>()
        is Array<*> -> raw.filterIsInstance<String>()
        is String -> if (raw.isEmpty()) emptyList() else listOf(raw)
        else -> emptyList()
    }

    /** Returns the command-line arguments stored under [KEY_COMMAND_LINE_ARGS] as a typed [Array] of [String]s. */
    fun getCommandLineArgsArray(): Array<String> = getCommandLineArgs().toTypedArray()

    /** Stores [args] under [KEY_COMMAND_LINE_ARGS] for the initial `@MainAppFragment`. */
    fun putCommandLineArgs(args: Array<String>) {
        map[KEY_COMMAND_LINE_ARGS] = args.toList()
    }

    /** Stores [args] under [KEY_COMMAND_LINE_ARGS] for the initial `@MainAppFragment`. */
    fun putCommandLineArgs(args: List<String>) {
        map[KEY_COMMAND_LINE_ARGS] = args.toList()
    }

    /**
     * Serializes supported entries (`null`, `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`,
     * and `List<String>`) into deterministic key-sorted typed JSON.
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append('{')
        val sortedKeys = map.keys.sorted()
        for (index in sortedKeys.indices) {
            if (index > 0) sb.append(',')
            val key = sortedKeys[index]
            val value = map[key]
            appendJsonString(sb, key)
            sb.append(':')
            appendTypedEntry(sb, key, value)
        }
        sb.append('}')
        return sb.toString()
    }

    /**
     * Encodes this bundle as unpadded UTF-8 Base64URL JSON for safe CLI / URL / IPC transport.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toBase64Url(): String {
        val bytes = toJson().encodeToByteArray()
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }

    private class JsonNumber(val raw: String)

    private class JsonParser(private val source: String) {
        private var pos: Int = 0

        fun parseRootObject(): LinkedHashMap<String, Any?> {
            skipWhitespace()
            if (pos >= source.length) {
                throw IllegalArgumentException("FragmentBundle JSON cannot be blank.")
            }
            val obj = parseObject()
            skipWhitespace()
            if (pos != source.length) {
                throw IllegalArgumentException("Unexpected trailing JSON content at index $pos.")
            }
            return obj
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= source.length) {
                throw IllegalArgumentException("Unexpected end of JSON input at index $pos.")
            }
            return when (val ch = source[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token '$ch' at index $pos.")
            }
        }

        private fun parseObject(): LinkedHashMap<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') {
                    throw IllegalArgumentException("Expected JSON object key string at index $pos.")
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                when (val delim = peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at index $pos, found '$delim'.")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<Any?>()
            if (peek() == ']') {
                pos++
                return items
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (val delim = peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return items
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at index $pos, found '$delim'.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < source.length) {
                val ch = source[pos++]
                when {
                    ch == '"' -> return sb.toString()
                    ch == '\\' -> {
                        if (pos >= source.length) {
                            throw IllegalArgumentException("Unterminated escape sequence in JSON string.")
                        }
                        when (val esc = source[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > source.length) {
                                    throw IllegalArgumentException("Truncated unicode escape at index $pos.")
                                }
                                val hex = source.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid unicode escape '\\u$hex'.")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid JSON escape '\\$esc' at index ${pos - 1}.")
                        }
                    }
                    ch < '\u0020' -> throw IllegalArgumentException("Unescaped control character in JSON string.")
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string literal.")
        }

        private fun parseNumber(): JsonNumber {
            val start = pos
            if (peek() == '-') pos++
            if (pos >= source.length) {
                throw IllegalArgumentException("Invalid JSON number at index $start.")
            }
            if (source[pos] == '0') {
                pos++
                if (pos < source.length && source[pos] in '0'..'9') {
                    throw IllegalArgumentException("Leading zeros are not allowed in JSON number at index $start.")
                }
            } else if (source[pos] in '1'..'9') {
                while (pos < source.length && source[pos] in '0'..'9') pos++
            } else {
                throw IllegalArgumentException("Invalid JSON number at index $start.")
            }
            if (pos < source.length && source[pos] == '.') {
                pos++
                if (pos >= source.length || source[pos] !in '0'..'9') {
                    throw IllegalArgumentException("Expected fraction digit in JSON number at index $pos.")
                }
                while (pos < source.length && source[pos] in '0'..'9') pos++
            }
            if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
                pos++
                if (pos < source.length && (source[pos] == '+' || source[pos] == '-')) pos++
                if (pos >= source.length || source[pos] !in '0'..'9') {
                    throw IllegalArgumentException("Expected exponent digit in JSON number at index $pos.")
                }
                while (pos < source.length && source[pos] in '0'..'9') pos++
            }
            return JsonNumber(source.substring(start, pos))
        }

        private fun <T> parseLiteral(expected: String, value: T): T {
            if (!source.startsWith(expected, pos)) {
                throw IllegalArgumentException("Invalid JSON literal at index $pos; expected '$expected'.")
            }
            pos += expected.length
            return value
        }

        private fun expect(ch: Char) {
            if (pos >= source.length || source[pos] != ch) {
                val actual = if (pos < source.length) "'${source[pos]}'" else "EOF"
                throw IllegalArgumentException("Expected '$ch' at index $pos but found $actual.")
            }
            pos++
        }

        private fun peek(): Char? = if (pos < source.length) source[pos] else null

        private fun skipWhitespace() {
            while (pos < source.length) {
                when (source[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> return
                }
            }
        }
    }

    companion object {
        /** Canonical bundle key under which `main(args)` command-line arguments are passed to the initial `@MainAppFragment`. */
        const val KEY_COMMAND_LINE_ARGS: String = "genericui:command_line_args"

        /** Canonical bundle key for target fragment ID when launching a secondary fragment across processes or URLs. */
        const val KEY_LAUNCH_FRAGMENT_ID: String = "__genericui_launch_fragment_id"

        /** Canonical bundle key for target fragment route when launching via route. */
        const val KEY_LAUNCH_ROUTE: String = "__genericui_launch_route"

        /** Canonical bundle key requesting opening a fragment in a new top-level window. */
        const val KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW: String = "__genericui_launch_open_additional_window"

        /** Canonical bundle key storing an explicit CLI or deep-link launch mode override when provided. */
        const val KEY_LAUNCH_MODE: String = "__genericui_launch_mode"

        /** Canonical bundle key requesting single-top reuse when launching a fragment. */
        const val KEY_LAUNCH_SINGLE_TOP: String = "__genericui_launch_single_top"

        /**
         * Creates a [FragmentBundle] populated with CLI launch flags (`--fragment-args-b64=<b64>`,
         * `--fragment=<id>`, `--route=<route>`, `--open-additional-window=<true|false>`,
         * `--launch-mode=<mode>`, `--single-top=<true|false>`)
         * and preserves the raw arguments under [KEY_COMMAND_LINE_ARGS].
         */
        fun fromCommandLineArgs(args: Array<String>): FragmentBundle =
            fromCommandLineArgs(args.toList())

        /**
         * Creates a [FragmentBundle] populated with CLI launch flags (`--fragment-args-b64=<b64>`,
         * `--fragment=<id>`, `--route=<route>`, `--open-additional-window=<true|false>`,
         * `--launch-mode=<mode>`, `--single-top=<true|false>`)
         * and preserves the raw arguments under [KEY_COMMAND_LINE_ARGS].
         */
        fun fromCommandLineArgs(args: List<String>): FragmentBundle {
            val bundle = FragmentBundle()
            for (arg in args) {
                if (arg.startsWith("--fragment-args-b64=")) {
                    val encoded = arg.removePrefix("--fragment-args-b64=")
                    if (encoded.isNotBlank()) {
                        bundle.putAll(fromBase64Url(encoded))
                    }
                }
            }
            for (arg in args) {
                when {
                    arg.startsWith("--fragment=") -> {
                        val fragmentId = arg.removePrefix("--fragment=")
                        if (fragmentId.isNotEmpty()) {
                            bundle.putString(KEY_LAUNCH_FRAGMENT_ID, fragmentId)
                        }
                    }
                    arg.startsWith("--route=") -> {
                        val route = arg.removePrefix("--route=")
                        if (route.isNotEmpty()) {
                            bundle.putString(KEY_LAUNCH_ROUTE, route)
                        }
                    }
                    arg.startsWith("--open-additional-window=") -> {
                        val raw = arg.removePrefix("--open-additional-window=").trim().lowercase()
                        when (raw) {
                            "true" -> bundle.putBoolean(KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, true)
                            "false" -> bundle.putBoolean(KEY_LAUNCH_OPEN_ADDITIONAL_WINDOW, false)
                            else -> throw IllegalArgumentException(
                                "Invalid --open-additional-window value '$raw'; expected 'true' or 'false'."
                            )
                        }
                    }
                    arg.startsWith("--launch-mode=") -> {
                        val mode = arg.removePrefix("--launch-mode=")
                        if (mode.isNotEmpty()) {
                            bundle.putString(KEY_LAUNCH_MODE, mode)
                        }
                    }
                    arg.startsWith("--single-top=") -> {
                        val raw = arg.removePrefix("--single-top=").trim().lowercase()
                        when (raw) {
                            "true" -> bundle.putBoolean(KEY_LAUNCH_SINGLE_TOP, true)
                            "false" -> bundle.putBoolean(KEY_LAUNCH_SINGLE_TOP, false)
                            else -> throw IllegalArgumentException(
                                "Invalid --single-top value '$raw'; expected 'true' or 'false'."
                            )
                        }
                    }
                }
            }
            bundle.putCommandLineArgs(args)
            return bundle
        }

        /**
         * Deserializes a [FragmentBundle] previously produced by [toJson] (or plain JSON primitives/string lists).
         */
        fun fromJson(json: String): FragmentBundle {
            val root = JsonParser(json).parseRootObject()
            val bundle = FragmentBundle()
            for ((key, rawValue) in root) {
                bundle.map[key] = decodeEntry(key, rawValue)
            }
            return bundle
        }

        /**
         * Decodes an unpadded or padded Base64URL JSON payload produced by [toBase64Url] into a [FragmentBundle].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromBase64Url(encoded: String): FragmentBundle {
            val trimmed = encoded.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("FragmentBundle Base64URL payload cannot be blank.")
            }
            val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(trimmed)
            return fromJson(bytes.decodeToString())
        }

        /**
         * Creates a bundle from key-value [pairs], e.g. `FragmentBundle.of("id" to 42, "title" to "Details")`.
         * Values are stored as-is (see [put]); `String`/`Int`/`Long`/`Float`/`Double`/`Boolean`/`List<String>` values
         * remain readable through the typed getters.
         */
        fun of(vararg pairs: Pair<String, Any?>): FragmentBundle {
            val bundle = FragmentBundle()
            for ((key, value) in pairs) {
                bundle.map[key] = value
            }
            return bundle
        }

        private fun appendTypedEntry(sb: StringBuilder, key: String, value: Any?) {
            sb.append('{')
            when (value) {
                null -> sb.append("\"t\":\"n\",\"v\":null")
                is String -> {
                    sb.append("\"t\":\"s\",\"v\":")
                    appendJsonString(sb, value)
                }
                is Boolean -> {
                    sb.append("\"t\":\"b\",\"v\":")
                    sb.append(if (value) "true" else "false")
                }
                is Int -> {
                    sb.append("\"t\":\"i\",\"v\":")
                    sb.append(value)
                }
                is Long -> {
                    sb.append("\"t\":\"l\",\"v\":")
                    sb.append(value)
                }
                is Double -> {
                    sb.append("\"t\":\"d\",\"v\":")
                    appendFloatingLiteral(sb, value.isFinite(), value.isNaN(), value < 0.0, value.toString())
                }
                is Float -> {
                    sb.append("\"t\":\"f\",\"v\":")
                    appendFloatingLiteral(sb, value.isFinite(), value.isNaN(), value < 0f, value.toString())
                }
                is List<*> -> {
                    if (!value.all { it is String }) {
                        throw IllegalArgumentException(
                            "Unsupported FragmentBundle list element for key '$key'; only List<String> is supported."
                        )
                    }
                    sb.append("\"t\":\"ls\",\"v\":[")
                    for (idx in value.indices) {
                        if (idx > 0) sb.append(',')
                        appendJsonString(sb, value[idx] as String)
                    }
                    sb.append(']')
                }
                is Array<*> -> {
                    if (!value.all { it is String }) {
                        throw IllegalArgumentException(
                            "Unsupported FragmentBundle array element for key '$key'; only String arrays/lists are supported."
                        )
                    }
                    sb.append("\"t\":\"ls\",\"v\":[")
                    for (idx in value.indices) {
                        if (idx > 0) sb.append(',')
                        appendJsonString(sb, value[idx] as String)
                    }
                    sb.append(']')
                }
                else -> throw IllegalArgumentException(
                    "Unsupported FragmentBundle value type '${value::class.simpleName}' for key '$key'."
                )
            }
            sb.append('}')
        }

        private fun appendFloatingLiteral(
            sb: StringBuilder,
            isFinite: Boolean,
            isNaN: Boolean,
            isNegative: Boolean,
            raw: String
        ) {
            when {
                isNaN -> sb.append("\"NaN\"")
                !isFinite && isNegative -> sb.append("\"-Infinity\"")
                !isFinite -> sb.append("\"Infinity\"")
                else -> sb.append(raw)
            }
        }

        private fun appendJsonString(sb: StringBuilder, text: String) {
            sb.append('"')
            for (ch in text) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (ch < '\u0020') {
                            sb.append("\\u")
                            val hex = ch.code.toString(16)
                            for (pad in 0 until (4 - hex.length)) {
                                sb.append('0')
                            }
                            sb.append(hex)
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            sb.append('"')
        }

        private fun decodeEntry(key: String, rawValue: Any?): Any? {
            if (rawValue is Map<*, *>) {
                val typeTag = rawValue["t"] as? String
                if (typeTag != null && rawValue.containsKey("v")) {
                    val v = rawValue["v"]
                    return when (typeTag) {
                        "n" -> null
                        "s" -> v as? String
                            ?: throw IllegalArgumentException("Expected String value for key '$key'.")
                        "b" -> v as? Boolean
                            ?: throw IllegalArgumentException("Expected Boolean value for key '$key'.")
                        "i" -> (v as? JsonNumber)?.raw?.toIntOrNull()
                            ?: throw IllegalArgumentException("Expected Int value for key '$key'.")
                        "l" -> (v as? JsonNumber)?.raw?.toLongOrNull()
                            ?: throw IllegalArgumentException("Expected Long value for key '$key'.")
                        "f" -> decodeFloat(key, v)
                        "d" -> decodeDouble(key, v)
                        "ls" -> decodeStringList(key, v)
                        else -> throw IllegalArgumentException("Unknown FragmentBundle type tag '$typeTag' for key '$key'.")
                    }
                }
            }
            return when (rawValue) {
                null -> null
                is String, is Boolean -> rawValue
                is JsonNumber -> {
                    val lex = rawValue.raw
                    if ('.' in lex || 'e' in lex || 'E' in lex) {
                        lex.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid Double value '$lex' for key '$key'.")
                    } else {
                        lex.toIntOrNull() ?: lex.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid integer value '$lex' for key '$key'.")
                    }
                }
                is List<*> -> decodeStringList(key, rawValue)
                else -> throw IllegalArgumentException("Unsupported JSON entry for key '$key'.")
            }
        }

        private fun decodeFloat(key: String, value: Any?): Float = when (value) {
            is String -> when (value) {
                "NaN" -> Float.NaN
                "Infinity" -> Float.POSITIVE_INFINITY
                "-Infinity" -> Float.NEGATIVE_INFINITY
                else -> value.toFloatOrNull()
                    ?: throw IllegalArgumentException("Invalid Float value '$value' for key '$key'.")
            }
            is JsonNumber -> value.raw.toFloatOrNull()
                ?: throw IllegalArgumentException("Invalid Float value '${value.raw}' for key '$key'.")
            else -> throw IllegalArgumentException("Expected Float value for key '$key'.")
        }

        private fun decodeDouble(key: String, value: Any?): Double = when (value) {
            is String -> when (value) {
                "NaN" -> Double.NaN
                "Infinity" -> Double.POSITIVE_INFINITY
                "-Infinity" -> Double.NEGATIVE_INFINITY
                else -> value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid Double value '$value' for key '$key'.")
            }
            is JsonNumber -> value.raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid Double value '${value.raw}' for key '$key'.")
            else -> throw IllegalArgumentException("Expected Double value for key '$key'.")
        }

        private fun decodeStringList(key: String, value: Any?): List<String> {
            val list = value as? List<*>
                ?: throw IllegalArgumentException("Expected List<String> value for key '$key'.")
            return list.map { element ->
                element as? String
                    ?: throw IllegalArgumentException("Expected String list element for key '$key'.")
            }
        }
    }
}
