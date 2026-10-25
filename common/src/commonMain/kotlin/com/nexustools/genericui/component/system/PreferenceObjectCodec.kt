package com.nexustools.genericui.component.system

import kotlin.reflect.KClass

/**
 * Object-to-[PreferenceElement] and JSON serializer/deserializer used by [PreferenceStore],
 * [com.nexustools.genericui.util.BinaryJson], and [com.nexustools.genericui.util.BinaryPlist].
 */
object PreferenceObjectCodec {

    private val typeAdapters = mutableMapOf<KClass<*>, PreferenceTypeAdapter<*>>()
    private val objectFactories = mutableMapOf<KClass<*>, (Map<String, PreferenceElement>) -> Any>()

    fun <T : Any> registerTypeAdapter(targetClass: KClass<T>, adapter: PreferenceTypeAdapter<T>) {
        typeAdapters[targetClass] = adapter
    }

    inline fun <reified T : Any> registerTypeAdapter(adapter: PreferenceTypeAdapter<T>) {
        registerTypeAdapter(T::class, adapter)
    }

    fun <T : Any> unregisterTypeAdapter(targetClass: KClass<T>) {
        typeAdapters.remove(targetClass)
    }

    fun <T : Any> registerObjectFactory(
        targetClass: KClass<T>,
        factory: (Map<String, PreferenceElement>) -> T
    ) {
        objectFactories[targetClass] = factory
    }

    inline fun <reified T : Any> registerObjectFactory(
        noinline factory: (Map<String, PreferenceElement>) -> T
    ) {
        registerObjectFactory(T::class, factory)
    }

    fun clearRegistrations() {
        typeAdapters.clear()
        objectFactories.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun toElement(value: Any?): PreferenceElement {
        if (value == null) return PreferenceElement.Null
        if (value is PreferenceElement) return value

        val adapter = typeAdapters[value::class] as? PreferenceTypeAdapter<Any>
        if (adapter != null) {
            return adapter.toElement(value)
        }

        return when (value) {
            is Boolean -> PreferenceElement.ofBoolean(value)
            is Byte -> PreferenceElement.ofByte(value)
            is Short -> PreferenceElement.ofShort(value)
            is Int -> PreferenceElement.ofInt(value)
            is Long -> PreferenceElement.ofLong(value)
            is Float -> PreferenceElement.ofFloat(value)
            is Double -> PreferenceElement.ofDouble(value)
            is Number -> PreferenceElement.ofNumber(value)
            is Char -> PreferenceElement.ofString(value.toString())
            is CharSequence -> PreferenceElement.ofString(value.toString())
            is Enum<*> -> PreferenceElement.ofString(value.name)
            is PreferenceSerializable -> {
                val fields = LinkedHashMap<String, PreferenceElement>()
                for ((k, v) in value.toPreferenceMap()) {
                    fields[k] = toElement(v)
                }
                PreferenceElement.ofObject(fields)
            }
            is Map<*, *> -> {
                val entries = LinkedHashMap<String, PreferenceElement>(value.size)
                for ((k, v) in value) {
                    if (k != null) {
                        entries[k.toString()] = toElement(v)
                    }
                }
                PreferenceElement.ofMap(entries)
            }
            is Iterable<*> -> PreferenceElement.ofArray(value.map { toElement(it) })
            is Array<*> -> PreferenceElement.ofArray(value.map { toElement(it) })
            is ByteArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofByte(it) })
            is ShortArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofShort(it) })
            is IntArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofInt(it) })
            is LongArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofLong(it) })
            is FloatArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofFloat(it) })
            is DoubleArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofDouble(it) })
            is BooleanArray -> PreferenceElement.ofArray(value.map { PreferenceElement.ofBoolean(it) })
            is CharArray -> PreferenceElement.ofString(value.concatToString())
            else -> {
                val extracted = PreferencePlatformReflector.extractObjectFields(value)
                if (extracted != null) {
                    val fields = LinkedHashMap<String, PreferenceElement>(extracted.size)
                    for ((k, v) in extracted) {
                        fields[k] = toElement(v)
                    }
                    PreferenceElement.ofObject(fields)
                } else {
                    PreferenceElement.ofString(value.toString())
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromElement(
        element: PreferenceElement?,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        if (element == null || element.isNull) return defaultValue

        if (targetClass == PreferenceElement::class) {
            return element as T
        }
        if (targetClass == Any::class) {
            return (element.toPlainValue() ?: defaultValue) as? T
        }

        val adapter = typeAdapters[targetClass] as? PreferenceTypeAdapter<T>
        if (adapter != null) {
            return runCatching { adapter.fromElement(element) }.getOrDefault(defaultValue)
        }

        val factory = objectFactories[targetClass] as? ((Map<String, PreferenceElement>) -> T)
        if (factory != null && (element.isObject || element.isMap)) {
            return runCatching { factory(element.mapEntries) }.getOrDefault(defaultValue)
        }

        val result: Any? = when (targetClass) {
            String::class, CharSequence::class -> element.asString(defaultValue as? String)
            Boolean::class -> element.asBoolean((defaultValue as? Boolean) ?: false)
            Int::class -> element.asInt((defaultValue as? Int) ?: 0)
            Long::class -> element.asLong((defaultValue as? Long) ?: 0L)
            Float::class -> element.asFloat((defaultValue as? Float) ?: 0f)
            Double::class -> element.asDouble((defaultValue as? Double) ?: 0.0)
            Byte::class -> element.asByte((defaultValue as? Byte) ?: 0)
            Short::class -> element.asShort((defaultValue as? Short) ?: 0)
            Number::class -> element.numberValue ?: (defaultValue as? Number) ?: 0
            Char::class -> element.asString()?.firstOrNull() ?: (defaultValue as? Char) ?: '\u0000'
            List::class, MutableList::class, Collection::class, Iterable::class -> {
                if (element.isArray) {
                    element.arrayElements.map { it.toPlainValue() }.toMutableList()
                } else {
                    defaultValue
                }
            }
            Set::class, MutableSet::class -> {
                if (element.isArray) {
                    val set = LinkedHashSet<Any?>(element.arrayElements.size)
                    for (item in element.arrayElements) {
                        set.add(item.toPlainValue())
                    }
                    set
                } else {
                    defaultValue
                }
            }
            Map::class, MutableMap::class, LinkedHashMap::class -> {
                if (element.isMap || element.isObject) {
                    val map = LinkedHashMap<String, Any?>(element.mapEntries.size)
                    for ((k, v) in element.mapEntries) {
                        map[k] = v.toPlainValue()
                    }
                    map
                } else {
                    defaultValue
                }
            }
            IntArray::class -> IntArray(element.arrayElements.size) { element.arrayElements[it].asInt() }
            LongArray::class -> LongArray(element.arrayElements.size) { element.arrayElements[it].asLong() }
            FloatArray::class -> FloatArray(element.arrayElements.size) { element.arrayElements[it].asFloat() }
            DoubleArray::class -> DoubleArray(element.arrayElements.size) { element.arrayElements[it].asDouble() }
            BooleanArray::class -> BooleanArray(element.arrayElements.size) { element.arrayElements[it].asBoolean() }
            ByteArray::class -> ByteArray(element.arrayElements.size) { element.arrayElements[it].asByte() }
            ShortArray::class -> ShortArray(element.arrayElements.size) { element.arrayElements[it].asShort() }
            else -> {
                if (element.isString) {
                    PreferencePlatformReflector.resolveEnumConstant(
                        targetClass,
                        element.stringValue ?: "",
                        defaultValue
                    )
                } else if (element.isObject || element.isMap) {
                    PreferencePlatformReflector.instantiateObject(
                        targetClass = targetClass,
                        fields = element.mapEntries,
                        defaultValue = defaultValue
                    )
                } else {
                    defaultValue
                }
            }
        }
        return (result as? T) ?: defaultValue
    }

    /**
     * Serializes any value or object into a standard UTF-8 JSON string.
     */
    fun toJson(value: Any?): String = elementToJson(toElement(value))

    /**
     * Serializes a [PreferenceElement] tree into a standard JSON string.
     */
    fun elementToJson(element: PreferenceElement): String {
        val sb = StringBuilder()
        writeJsonElement(sb, element)
        return sb.toString()
    }

    /**
     * Parses a JSON string into a [PreferenceElement] tree.
     */
    fun fromJsonElement(jsonText: String): PreferenceElement {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return PreferenceElement.Null
        val parser = JsonReader(trimmed)
        return parser.readValue()
    }

    /**
     * Parses a JSON string into plain Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun fromJson(jsonText: String): Any? = fromJsonElement(jsonText).toPlainValue()

    /**
     * Parses a JSON string directly into an instance of [targetClass].
     */
    fun <T : Any> fromJsonAs(
        jsonText: String,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        if (jsonText.isBlank()) return defaultValue
        val element = runCatching { fromJsonElement(jsonText) }.getOrNull() ?: return defaultValue
        return fromElement(element, targetClass, defaultValue)
    }

    inline fun <reified T : Any> fromJsonAs(
        jsonText: String,
        defaultValue: T? = null
    ): T? = fromJsonAs(jsonText, T::class, defaultValue)

    private fun writeJsonElement(sb: StringBuilder, element: PreferenceElement) {
        when (element.kind) {
            PreferenceElementKind.NULL -> sb.append("null")
            PreferenceElementKind.BOOLEAN -> sb.append(if (element.booleanValue == true) "true" else "false")
            PreferenceElementKind.NUMBER -> {
                val n = element.numberValue
                val d = n?.toDouble() ?: 0.0
                if (d.isNaN() || d.isInfinite()) {
                    sb.append("0")
                } else {
                    sb.append(n.toString())
                }
            }
            PreferenceElementKind.STRING -> writeJsonString(sb, element.stringValue ?: "")
            PreferenceElementKind.ARRAY -> {
                sb.append('[')
                element.arrayElements.forEachIndexed { idx, child ->
                    if (idx > 0) sb.append(',')
                    writeJsonElement(sb, child)
                }
                sb.append(']')
            }
            PreferenceElementKind.MAP, PreferenceElementKind.OBJECT -> {
                sb.append('{')
                var first = true
                for ((k, v) in element.mapEntries) {
                    if (!first) sb.append(',')
                    first = false
                    writeJsonString(sb, k)
                    sb.append(':')
                    writeJsonElement(sb, v)
                }
                sb.append('}')
            }
        }
    }

    private fun writeJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        val hex = ch.code.toString(16).padStart(4, '0')
                        sb.append("\\u").append(hex)
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    private class JsonReader(private val src: String) {
        private var pos: Int = 0
        private val len: Int = src.length

        fun readValue(): PreferenceElement {
            skipWhitespace()
            if (pos >= len) return PreferenceElement.Null
            return when (src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> PreferenceElement.ofString(readString())
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> readNumber()
            }
        }

        private fun readObject(): PreferenceElement {
            pos++ // '{'
            skipWhitespace()
            val map = LinkedHashMap<String, PreferenceElement>()
            if (pos < len && src[pos] == '}') {
                pos++
                return PreferenceElement.ofObject(map)
            }
            while (pos < len) {
                skipWhitespace()
                if (pos >= len || src[pos] == '}') {
                    if (pos < len) pos++
                    break
                }
                val key = if (src[pos] == '"') readString() else readUnquotedToken()
                skipWhitespace()
                if (pos < len && src[pos] == ':') pos++
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (pos < len && src[pos] == ',') {
                    pos++
                } else if (pos < len && src[pos] == '}') {
                    pos++
                    break
                }
            }
            return PreferenceElement.ofObject(map)
        }

        private fun readArray(): PreferenceElement {
            pos++ // '['
            skipWhitespace()
            val list = ArrayList<PreferenceElement>()
            if (pos < len && src[pos] == ']') {
                pos++
                return PreferenceElement.ofArray(list)
            }
            while (pos < len) {
                skipWhitespace()
                if (pos >= len || src[pos] == ']') {
                    if (pos < len) pos++
                    break
                }
                list.add(readValue())
                skipWhitespace()
                if (pos < len && src[pos] == ',') {
                    pos++
                } else if (pos < len && src[pos] == ']') {
                    pos++
                    break
                }
            }
            return PreferenceElement.ofArray(list)
        }

        private fun readString(): String {
            if (pos < len && src[pos] == '"') pos++
            val sb = StringBuilder()
            while (pos < len) {
                val c = src[pos++]
                if (c == '"') break
                if (c == '\\' && pos < len) {
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= len) {
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: 0
                                sb.append(code.toChar())
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun readBoolean(): PreferenceElement {
            if (src.startsWith("true", pos)) {
                pos += 4
                return PreferenceElement.ofBoolean(true)
            }
            if (src.startsWith("false", pos)) {
                pos += 5
                return PreferenceElement.ofBoolean(false)
            }
            return PreferenceElement.ofString(readUnquotedToken())
        }

        private fun readNull(): PreferenceElement {
            if (src.startsWith("null", pos)) {
                pos += 4
                return PreferenceElement.Null
            }
            return PreferenceElement.ofString(readUnquotedToken())
        }

        private fun readNumber(): PreferenceElement {
            val token = readUnquotedToken()
            val isFloating = token.contains('.') || token.contains('e') || token.contains('E')
            if (isFloating) {
                val d = token.toDoubleOrNull() ?: return PreferenceElement.ofString(token)
                return PreferenceElement.ofDouble(d)
            }
            val l = token.toLongOrNull() ?: return PreferenceElement.ofString(token)
            return if (l in Int.MIN_VALUE..Int.MAX_VALUE) {
                PreferenceElement.ofInt(l.toInt())
            } else {
                PreferenceElement.ofLong(l)
            }
        }

        private fun readUnquotedToken(): String {
            val start = pos
            while (pos < len) {
                val c = src[pos]
                if (c.isWhitespace() || c == ',' || c == '}' || c == ']' || c == ':') break
                pos++
            }
            return src.substring(start, pos)
        }

        private fun skipWhitespace() {
            while (pos < len && src[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
