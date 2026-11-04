package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.io.WritableByteBuffer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KClass

/**
 * Cross-platform binary JSON encoder and decoder (`com.nexustools.genericui.util.BinaryJson`).
 *
 * Operates directly on [Writable] and [Readable] streams (automatically supporting [WritableByteBuffer],
 * [ReadableByteBuffer], file streams, network sockets, HTTP request/response streams, and database streams).
 *
 * Encodes and decodes structured values ([PreferenceElement] trees, objects, maps, arrays, strings,
 * numbers, booleans, and `null`) using a 1-byte type tag followed by big-endian content bytes instead of text:
 * - `0x00` ([TYPE_NULL]): 0 bytes
 * - `0x01` ([TYPE_BOOLEAN]): 1 byte (`0x00` = `false`, `0x01` = `true`)
 * - `0x02` ([TYPE_BYTE]): 1 byte signed `Int8`
 * - `0x03` ([TYPE_SHORT]): 2 bytes big-endian `Int16`
 * - `0x04` ([TYPE_INT]): 4 bytes big-endian `Int32`
 * - `0x05` ([TYPE_LONG]): 8 bytes big-endian `Int64`
 * - `0x06` ([TYPE_FLOAT]): 4 bytes big-endian IEEE-754 `Float`
 * - `0x07` ([TYPE_DOUBLE]): 8 bytes big-endian IEEE-754 `Double`
 * - `0x08` ([TYPE_STRING]): 4-byte UTF-8 byte length + UTF-8 bytes
 * - `0x09` ([TYPE_ARRAY]): 4-byte element count + encoded elements
 * - `0x0A` ([TYPE_MAP]): 4-byte entry count + pairs of `[4-byte keyLen + UTF-8 key][encoded value]`
 * - `0x0B` ([TYPE_OBJECT]): 4-byte field count + pairs of `[4-byte fieldNameLen + UTF-8 fieldName][encoded field value]`
 */
object BinaryJson {

    const val TYPE_NULL: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_BYTE: Byte = 0x02
    const val TYPE_SHORT: Byte = 0x03
    const val TYPE_INT: Byte = 0x04
    const val TYPE_LONG: Byte = 0x05
    const val TYPE_FLOAT: Byte = 0x06
    const val TYPE_DOUBLE: Byte = 0x07
    const val TYPE_STRING: Byte = 0x08
    const val TYPE_ARRAY: Byte = 0x09
    const val TYPE_MAP: Byte = 0x0A
    const val TYPE_OBJECT: Byte = 0x0B

    /**
     * Encodes a [PreferenceElement] tree directly into any [Writable] stream (including [WritableByteBuffer]).
     */
    fun encodeElement(element: PreferenceElement, writable: Writable) {
        writeElement(writable, element)
    }

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` directly into any [Writable] stream.
     */
    fun encode(value: Any?, writable: Writable) {
        encodeElement(PreferenceObjectCodec.toElement(value), writable)
    }

    /**
     * Encodes a [PreferenceElement] tree into a big-endian [ByteBuffer].
     */
    fun encodeElementToBuffer(element: PreferenceElement): ByteBuffer =
        WritableByteBuffer(initialCapacity = 256, byteOrder = ByteOrder.BIG_ENDIAN)
            .also { encodeElement(element, it) }
            .toByteBuffer()

    /**
     * Encodes a [PreferenceElement] tree into binary JSON bytes.
     */
    fun encodeElement(element: PreferenceElement): ByteArray =
        WritableByteBuffer(initialCapacity = 256, byteOrder = ByteOrder.BIG_ENDIAN)
            .also { encodeElement(element, it) }
            .toByteArray()

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into binary JSON bytes.
     */
    fun encode(value: Any?): ByteArray =
        encodeElement(PreferenceObjectCodec.toElement(value))

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into a [ByteBuffer].
     */
    fun encodeToBuffer(value: Any?): ByteBuffer =
        encodeElementToBuffer(PreferenceObjectCodec.toElement(value))

    /**
     * Asynchronously decodes a [PreferenceElement] tree directly from any [Readable] stream
     * (including [ReadableByteBuffer]).
     */
    suspend fun decodeElement(readable: Readable): PreferenceElement =
        readElement(readable)

    /**
     * Asynchronously decodes a [Readable] stream into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    suspend fun decode(readable: Readable): Any? =
        decodeElement(readable).toPlainValue()

    /**
     * Asynchronously decodes a [Readable] stream directly into an instance of [targetClass].
     */
    suspend fun <T : Any> decodeAs(
        readable: Readable,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        val element = runCatching { decodeElement(readable) }.getOrNull() ?: return defaultValue
        if (element.isNull) return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    suspend inline fun <reified T : Any> decodeAs(
        readable: Readable,
        defaultValue: T? = null
    ): T? = decodeAs(readable, T::class, defaultValue)

    /**
     * Decodes binary JSON bytes into a [PreferenceElement] tree.
     */
    fun decodeElement(bytes: ByteArray): PreferenceElement {
        if (bytes.isEmpty()) return PreferenceElement.Null
        return decodeElement(ByteBuffer.wrap(bytes, byteOrder = ByteOrder.BIG_ENDIAN))
    }

    /**
     * Decodes a binary JSON [ByteBuffer] into a [PreferenceElement] tree using [ReadableByteBuffer].
     */
    fun decodeElement(buffer: ByteBuffer): PreferenceElement {
        if (!buffer.hasRemaining) return PreferenceElement.Null
        val reader = ReadableByteBuffer(buffer, buffer.position, ByteOrder.BIG_ENDIAN)
        val result = runNonSuspending { decodeElement(reader as Readable) }
        buffer.position = reader.position.coerceIn(0, buffer.limit)
        return result
    }

    /**
     * Decodes binary JSON bytes into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(bytes: ByteArray): Any? =
        decodeElement(bytes).toPlainValue()

    /**
     * Decodes a binary JSON [ByteBuffer] into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(buffer: ByteBuffer): Any? =
        decodeElement(buffer).toPlainValue()

    /**
     * Decodes binary JSON bytes directly into an instance of [targetClass].
     */
    fun <T : Any> decodeAs(
        bytes: ByteArray,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        if (bytes.isEmpty()) return defaultValue
        val element = runCatching { decodeElement(bytes) }.getOrNull() ?: return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    /**
     * Decodes a binary JSON [ByteBuffer] directly into an instance of [targetClass].
     */
    fun <T : Any> decodeAs(
        buffer: ByteBuffer,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        if (!buffer.hasRemaining) return defaultValue
        val element = runCatching { decodeElement(buffer) }.getOrNull() ?: return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    inline fun <reified T : Any> decodeAs(
        bytes: ByteArray,
        defaultValue: T? = null
    ): T? = decodeAs(bytes, T::class, defaultValue)

    inline fun <reified T : Any> decodeAs(
        buffer: ByteBuffer,
        defaultValue: T? = null
    ): T? = decodeAs(buffer, T::class, defaultValue)

    private fun writeElement(out: Writable, element: PreferenceElement) {
        when (element.kind) {
            PreferenceElementKind.NULL -> out.writeByte(TYPE_NULL)
            PreferenceElementKind.BOOLEAN -> {
                out.writeByte(TYPE_BOOLEAN)
                out.writeBoolean(element.booleanValue == true)
            }
            PreferenceElementKind.NUMBER -> {
                val n = element.numberValue ?: 0
                when (element.numberTypeTag) {
                    PreferenceElement.NUMBER_TAG_BYTE -> {
                        out.writeByte(TYPE_BYTE)
                        out.writeByte(n.toByte())
                    }
                    PreferenceElement.NUMBER_TAG_SHORT -> {
                        out.writeByte(TYPE_SHORT)
                        out.writeShort(n.toShort(), ByteOrder.BIG_ENDIAN)
                    }
                    PreferenceElement.NUMBER_TAG_INT -> {
                        out.writeByte(TYPE_INT)
                        out.writeInt(n.toInt(), ByteOrder.BIG_ENDIAN)
                    }
                    PreferenceElement.NUMBER_TAG_LONG -> {
                        out.writeByte(TYPE_LONG)
                        out.writeLong(n.toLong(), ByteOrder.BIG_ENDIAN)
                    }
                    PreferenceElement.NUMBER_TAG_FLOAT -> {
                        out.writeByte(TYPE_FLOAT)
                        out.writeFloat(n.toFloat(), ByteOrder.BIG_ENDIAN)
                    }
                    PreferenceElement.NUMBER_TAG_DOUBLE -> {
                        out.writeByte(TYPE_DOUBLE)
                        out.writeDouble(n.toDouble(), ByteOrder.BIG_ENDIAN)
                    }
                    else -> {
                        out.writeByte(TYPE_DOUBLE)
                        out.writeDouble(n.toDouble(), ByteOrder.BIG_ENDIAN)
                    }
                }
            }
            PreferenceElementKind.STRING -> {
                out.writeByte(TYPE_STRING)
                writeUtf8(out, element.stringValue ?: "")
            }
            PreferenceElementKind.ARRAY -> {
                out.writeByte(TYPE_ARRAY)
                out.writeInt(element.arrayElements.size, ByteOrder.BIG_ENDIAN)
                for (item in element.arrayElements) {
                    writeElement(out, item)
                }
            }
            PreferenceElementKind.MAP -> {
                out.writeByte(TYPE_MAP)
                out.writeInt(element.mapEntries.size, ByteOrder.BIG_ENDIAN)
                for ((k, v) in element.mapEntries) {
                    writeUtf8(out, k)
                    writeElement(out, v)
                }
            }
            PreferenceElementKind.OBJECT -> {
                out.writeByte(TYPE_OBJECT)
                out.writeInt(element.mapEntries.size, ByteOrder.BIG_ENDIAN)
                for ((k, v) in element.mapEntries) {
                    writeUtf8(out, k)
                    writeElement(out, v)
                }
            }
        }
    }

    private fun writeUtf8(out: Writable, str: String) {
        val utf8 = str.encodeToByteArray()
        out.writeInt(utf8.size, ByteOrder.BIG_ENDIAN)
        out.writeBytes(utf8, 0, utf8.size)
    }

    private suspend fun readElement(readable: Readable): PreferenceElement {
        val tag = readable.readByteOrNull() ?: return PreferenceElement.Null
        return when (tag) {
            TYPE_NULL -> PreferenceElement.Null
            TYPE_BOOLEAN -> PreferenceElement.ofBoolean((readable.readByteOrNull() ?: 0.toByte()) != 0.toByte())
            TYPE_BYTE -> PreferenceElement.ofByte(readable.readByteOrNull() ?: 0)
            TYPE_SHORT -> {
                val buf = readable.tryReadFully(2) ?: return PreferenceElement.ofShort(0)
                PreferenceElement.ofShort(buf.readShort(0, ByteOrder.BIG_ENDIAN))
            }
            TYPE_INT -> {
                val buf = readable.tryReadFully(4) ?: return PreferenceElement.ofInt(0)
                PreferenceElement.ofInt(buf.readInt(0, ByteOrder.BIG_ENDIAN))
            }
            TYPE_LONG -> {
                val buf = readable.tryReadFully(8) ?: return PreferenceElement.ofLong(0L)
                PreferenceElement.ofLong(buf.readLong(0, ByteOrder.BIG_ENDIAN))
            }
            TYPE_FLOAT -> {
                val buf = readable.tryReadFully(4) ?: return PreferenceElement.ofFloat(0f)
                PreferenceElement.ofFloat(buf.readFloat(0, ByteOrder.BIG_ENDIAN))
            }
            TYPE_DOUBLE -> {
                val buf = readable.tryReadFully(8) ?: return PreferenceElement.ofDouble(0.0)
                PreferenceElement.ofDouble(buf.readDouble(0, ByteOrder.BIG_ENDIAN))
            }
            TYPE_STRING -> PreferenceElement.ofString(readUtf8(readable))
            TYPE_ARRAY -> {
                val countBuf = readable.tryReadFully(4) ?: return PreferenceElement.ofArray(emptyList())
                val count = countBuf.readInt(0, ByteOrder.BIG_ENDIAN).coerceAtLeast(0)
                val list = ArrayList<PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!readable.isReadable || readable.isEnded) break
                    list.add(readElement(readable))
                }
                PreferenceElement.ofArray(list)
            }
            TYPE_MAP -> {
                val countBuf = readable.tryReadFully(4) ?: return PreferenceElement.ofMap(emptyMap())
                val count = countBuf.readInt(0, ByteOrder.BIG_ENDIAN).coerceAtLeast(0)
                val map = LinkedHashMap<String, PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!readable.isReadable || readable.isEnded) break
                    val key = readUtf8(readable)
                    val value = readElement(readable)
                    map[key] = value
                }
                PreferenceElement.ofMap(map)
            }
            TYPE_OBJECT -> {
                val countBuf = readable.tryReadFully(4) ?: return PreferenceElement.ofObject(emptyMap())
                val count = countBuf.readInt(0, ByteOrder.BIG_ENDIAN).coerceAtLeast(0)
                val fields = LinkedHashMap<String, PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!readable.isReadable || readable.isEnded) break
                    val fieldName = readUtf8(readable)
                    val fieldVal = readElement(readable)
                    fields[fieldName] = fieldVal
                }
                PreferenceElement.ofObject(fields)
            }
            else -> PreferenceElement.Null
        }
    }

    private suspend fun readUtf8(readable: Readable): String {
        val lenBuf = readable.tryReadFully(4) ?: return ""
        val len = lenBuf.readInt(0, ByteOrder.BIG_ENDIAN).coerceAtLeast(0)
        if (len == 0) return ""
        val payload = readable.tryReadFully(len) ?: return ""
        return payload.toString("utf8")
    }

    private fun <T> runNonSuspending(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome ->
            result = outcome
        })
        return result?.getOrThrow()
            ?: throw IllegalStateException("Synchronous ReadableByteBuffer suspended unexpectedly")
    }
}
