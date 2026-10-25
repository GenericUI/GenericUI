package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import kotlin.reflect.KClass

/**
 * Cross-platform binary JSON encoder and decoder (`com.nexustools.genericui.util.BinaryJson`).
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
     * Encodes a [PreferenceElement] tree into binary JSON bytes.
     */
    fun encodeElement(element: PreferenceElement): ByteArray {
        val out = ByteWriter()
        writeElement(out, element)
        return out.toByteArray()
    }

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into binary JSON bytes.
     */
    fun encode(value: Any?): ByteArray =
        encodeElement(PreferenceObjectCodec.toElement(value))

    /**
     * Decodes binary JSON bytes into a [PreferenceElement] tree.
     */
    fun decodeElement(bytes: ByteArray): PreferenceElement {
        if (bytes.isEmpty()) return PreferenceElement.Null
        val reader = ByteReader(bytes)
        return readElement(reader)
    }

    /**
     * Decodes binary JSON bytes into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(bytes: ByteArray): Any? =
        decodeElement(bytes).toPlainValue()

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

    inline fun <reified T : Any> decodeAs(
        bytes: ByteArray,
        defaultValue: T? = null
    ): T? = decodeAs(bytes, T::class, defaultValue)

    private fun writeElement(out: ByteWriter, element: PreferenceElement) {
        when (element.kind) {
            PreferenceElementKind.NULL -> out.writeByte(TYPE_NULL)
            PreferenceElementKind.BOOLEAN -> {
                out.writeByte(TYPE_BOOLEAN)
                out.writeByte(if (element.booleanValue == true) 1 else 0)
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
                        out.writeShort(n.toShort())
                    }
                    PreferenceElement.NUMBER_TAG_INT -> {
                        out.writeByte(TYPE_INT)
                        out.writeInt(n.toInt())
                    }
                    PreferenceElement.NUMBER_TAG_LONG -> {
                        out.writeByte(TYPE_LONG)
                        out.writeLong(n.toLong())
                    }
                    PreferenceElement.NUMBER_TAG_FLOAT -> {
                        out.writeByte(TYPE_FLOAT)
                        out.writeInt(n.toFloat().toRawBits())
                    }
                    PreferenceElement.NUMBER_TAG_DOUBLE -> {
                        out.writeByte(TYPE_DOUBLE)
                        out.writeLong(n.toDouble().toRawBits())
                    }
                    else -> {
                        out.writeByte(TYPE_DOUBLE)
                        out.writeLong(n.toDouble().toRawBits())
                    }
                }
            }
            PreferenceElementKind.STRING -> {
                out.writeByte(TYPE_STRING)
                out.writeUtf8(element.stringValue ?: "")
            }
            PreferenceElementKind.ARRAY -> {
                out.writeByte(TYPE_ARRAY)
                out.writeInt(element.arrayElements.size)
                for (item in element.arrayElements) {
                    writeElement(out, item)
                }
            }
            PreferenceElementKind.MAP -> {
                out.writeByte(TYPE_MAP)
                out.writeInt(element.mapEntries.size)
                for ((k, v) in element.mapEntries) {
                    out.writeUtf8(k)
                    writeElement(out, v)
                }
            }
            PreferenceElementKind.OBJECT -> {
                out.writeByte(TYPE_OBJECT)
                out.writeInt(element.mapEntries.size)
                for ((k, v) in element.mapEntries) {
                    out.writeUtf8(k)
                    writeElement(out, v)
                }
            }
        }
    }

    private fun readElement(reader: ByteReader): PreferenceElement {
        if (!reader.hasRemaining()) return PreferenceElement.Null
        return when (reader.readByte()) {
            TYPE_NULL -> PreferenceElement.Null
            TYPE_BOOLEAN -> PreferenceElement.ofBoolean(reader.readByte() != 0.toByte())
            TYPE_BYTE -> PreferenceElement.ofByte(reader.readByte())
            TYPE_SHORT -> PreferenceElement.ofShort(reader.readShort())
            TYPE_INT -> PreferenceElement.ofInt(reader.readInt())
            TYPE_LONG -> PreferenceElement.ofLong(reader.readLong())
            TYPE_FLOAT -> PreferenceElement.ofFloat(Float.fromBits(reader.readInt()))
            TYPE_DOUBLE -> PreferenceElement.ofDouble(Double.fromBits(reader.readLong()))
            TYPE_STRING -> PreferenceElement.ofString(reader.readUtf8())
            TYPE_ARRAY -> {
                val count = reader.readInt().coerceAtLeast(0)
                val list = ArrayList<PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!reader.hasRemaining()) break
                    list.add(readElement(reader))
                }
                PreferenceElement.ofArray(list)
            }
            TYPE_MAP -> {
                val count = reader.readInt().coerceAtLeast(0)
                val map = LinkedHashMap<String, PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!reader.hasRemaining()) break
                    val key = reader.readUtf8()
                    val value = readElement(reader)
                    map[key] = value
                }
                PreferenceElement.ofMap(map)
            }
            TYPE_OBJECT -> {
                val count = reader.readInt().coerceAtLeast(0)
                val fields = LinkedHashMap<String, PreferenceElement>(count)
                for (i in 0 until count) {
                    if (!reader.hasRemaining()) break
                    val fieldName = reader.readUtf8()
                    val fieldVal = readElement(reader)
                    fields[fieldName] = fieldVal
                }
                PreferenceElement.ofObject(fields)
            }
            else -> PreferenceElement.Null
        }
    }

    private class ByteWriter(initialCapacity: Int = 64) {
        private var buf = ByteArray(initialCapacity)
        private var size = 0

        private fun ensureCapacity(extra: Int) {
            val needed = size + extra
            if (needed <= buf.size) return
            var newCap = buf.size * 2
            while (newCap < needed) newCap *= 2
            buf = buf.copyOf(newCap)
        }

        fun writeByte(value: Byte) {
            ensureCapacity(1)
            buf[size++] = value
        }

        fun writeShort(value: Short) {
            ensureCapacity(2)
            val v = value.toInt()
            buf[size++] = ((v ushr 8) and 0xFF).toByte()
            buf[size++] = (v and 0xFF).toByte()
        }

        fun writeInt(value: Int) {
            ensureCapacity(4)
            buf[size++] = ((value ushr 24) and 0xFF).toByte()
            buf[size++] = ((value ushr 16) and 0xFF).toByte()
            buf[size++] = ((value ushr 8) and 0xFF).toByte()
            buf[size++] = (value and 0xFF).toByte()
        }

        fun writeLong(value: Long) {
            ensureCapacity(8)
            buf[size++] = ((value ushr 56) and 0xFF).toByte()
            buf[size++] = ((value ushr 48) and 0xFF).toByte()
            buf[size++] = ((value ushr 40) and 0xFF).toByte()
            buf[size++] = ((value ushr 32) and 0xFF).toByte()
            buf[size++] = ((value ushr 24) and 0xFF).toByte()
            buf[size++] = ((value ushr 16) and 0xFF).toByte()
            buf[size++] = ((value ushr 8) and 0xFF).toByte()
            buf[size++] = (value and 0xFF).toByte()
        }

        fun writeUtf8(str: String) {
            val utf8 = str.encodeToByteArray()
            writeInt(utf8.size)
            ensureCapacity(utf8.size)
            utf8.copyInto(buf, destinationOffset = size)
            size += utf8.size
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private class ByteReader(private val data: ByteArray) {
        private var pos = 0

        fun hasRemaining(): Boolean = pos < data.size

        fun readByte(): Byte {
            if (pos >= data.size) return 0
            return data[pos++]
        }

        fun readShort(): Short {
            val b0 = (readByte().toInt() and 0xFF)
            val b1 = (readByte().toInt() and 0xFF)
            return ((b0 shl 8) or b1).toShort()
        }

        fun readInt(): Int {
            val b0 = (readByte().toInt() and 0xFF)
            val b1 = (readByte().toInt() and 0xFF)
            val b2 = (readByte().toInt() and 0xFF)
            val b3 = (readByte().toInt() and 0xFF)
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        fun readLong(): Long {
            val hi = readInt().toLong() and 0xFFFFFFFFL
            val lo = readInt().toLong() and 0xFFFFFFFFL
            return (hi shl 32) or lo
        }

        fun readUtf8(): String {
            val len = readInt().coerceIn(0, data.size - pos)
            if (len <= 0) return ""
            val str = data.decodeToString(startIndex = pos, endIndex = pos + len)
            pos += len
            return str
        }
    }
}
