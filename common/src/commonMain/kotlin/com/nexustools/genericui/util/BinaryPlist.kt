package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import kotlin.reflect.KClass

/**
 * Cross-platform Apple Binary Property List (`bplist00`) encoder and decoder
 * (`com.nexustools.genericui.util.BinaryPlist`).
 *
 * Strictly encodes and decodes Apple's binary property list format (`bplist00`):
 * 1. **8-Byte Header**: ASCII `"bplist00"` (`0x62 0x70 0x6C 0x69 0x73 0x74 0x30 0x30`).
 * 2. **Object Table**: Flattened table of objects where each object begins with a 1-byte marker
 *    (`high nibble` = type, `low nibble` = length/info, with `0x0F` indicating an extended integer count):
 *    - `0x00`: `null`
 *    - `0x08`: `false` (`Boolean`)
 *    - `0x09`: `true` (`Boolean`)
 *    - `0x10..0x13`: `Int` / `Long` (`2^n` bytes: 1, 2, 4, or 8 bytes big-endian)
 *    - `0x22`: `Float` (4 bytes IEEE-754 big-endian)
 *    - `0x23`: `Double` (8 bytes IEEE-754 big-endian)
 *    - `0x5n`: ASCII `String` (`n` bytes, or `0x5F` + int count + ASCII bytes)
 *    - `0x6n`: UTF-16BE `String` (`n` UTF-16 code units, or `0x6F` + int count + UTF-16BE bytes)
 *    - `0xAn`: `Array` (`n` object reference indices of `objectRefSize` bytes each)
 *    - `0xDn`: `Dict` (`Map` / serialized `Object`: `n` key object reference indices followed by `n` value object reference indices)
 * 3. **Offset Table**: `numObjects` entries of `offsetIntSize` bytes each pointing to the byte offset of each object.
 * 4. **32-Byte Trailer**: 6 zero bytes, `offsetIntSize` (1 byte), `objectRefSize` (1 byte),
 *    `numObjects` (8 bytes big-endian), `topObjectIndex` (8 bytes big-endian), and `offsetTableStart` (8 bytes big-endian).
 */
object BinaryPlist {

    const val MAGIC_HEADER: String = "bplist00"

    private val HEADER_BYTES: ByteArray = byteArrayOf(
        0x62, 0x70, 0x6C, 0x69, 0x73, 0x74, 0x30, 0x30
    )

    /**
     * Returns `true` if [bytes] begins with the 8-byte `"bplist00"` header and includes a 32-byte trailer.
     */
    fun isBinaryPlist(bytes: ByteArray): Boolean {
        if (bytes.size < 40) return false
        for (i in 0 until 8) {
            if (bytes[i] != HEADER_BYTES[i]) return false
        }
        return true
    }

    /**
     * Encodes a [PreferenceElement] tree into standard Apple `"bplist00"` binary property list bytes.
     */
    fun encodeElement(element: PreferenceElement): ByteArray {
        val objects = ArrayList<PlistNode>()
        flattenElement(element, objects)

        val numObjects = objects.size
        val objectRefSize = when {
            numObjects < 256 -> 1
            numObjects < 65536 -> 2
            else -> 4
        }

        val out = ByteWriter(128 + numObjects * 16)
        out.writeBytes(HEADER_BYTES)

        val offsets = IntArray(numObjects)
        for (i in 0 until numObjects) {
            offsets[i] = out.size
            writePlistNode(out, objects[i], objectRefSize)
        }

        val offsetTableStart = out.size
        val maxOffset = if (numObjects > 0) offsets.maxOrNull() ?: 0 else 0
        val offsetIntSize = when {
            maxOffset < 256 -> 1
            maxOffset < 65536 -> 2
            else -> 4
        }

        for (offset in offsets) {
            out.writeUnsignedInt(offset, offsetIntSize)
        }

        // 32-byte trailer
        repeat(6) { out.writeByte(0) }
        out.writeByte(offsetIntSize.toByte())
        out.writeByte(objectRefSize.toByte())
        out.writeLong(numObjects.toLong())
        out.writeLong(0L) // topObjectIndex = 0
        out.writeLong(offsetTableStart.toLong())

        return out.toByteArray()
    }

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into `"bplist00"` binary bytes.
     */
    fun encode(value: Any?): ByteArray =
        encodeElement(PreferenceObjectCodec.toElement(value))

    /**
     * Decodes a `"bplist00"` binary property list [ByteArray] into a [PreferenceElement] tree.
     */
    fun decodeElement(bytes: ByteArray): PreferenceElement {
        if (!isBinaryPlist(bytes)) return PreferenceElement.Null

        val trailerPos = bytes.size - 32
        val offsetIntSize = bytes[trailerPos + 6].toInt() and 0xFF
        val objectRefSize = bytes[trailerPos + 7].toInt() and 0xFF
        val numObjects = readLongBE(bytes, trailerPos + 8).toInt()
        val topObjectIndex = readLongBE(bytes, trailerPos + 16).toInt()
        val offsetTableStart = readLongBE(bytes, trailerPos + 24).toInt()

        if (numObjects <= 0 || offsetIntSize !in setOf(1, 2, 4, 8) || objectRefSize !in setOf(1, 2, 4, 8)) {
            return PreferenceElement.Null
        }
        if (offsetTableStart < 8 || offsetTableStart + numObjects * offsetIntSize > trailerPos) {
            return PreferenceElement.Null
        }

        val offsets = IntArray(numObjects)
        for (i in 0 until numObjects) {
            offsets[i] = readUnsignedIntBE(bytes, offsetTableStart + i * offsetIntSize, offsetIntSize)
        }

        if (topObjectIndex !in 0 until numObjects) {
            return PreferenceElement.Null
        }

        return decodeObjectAtIndex(
            bytes = bytes,
            offsets = offsets,
            objectRefSize = objectRefSize,
            index = topObjectIndex,
            visiting = mutableSetOf()
        )
    }

    /**
     * Decodes a `"bplist00"` binary property list into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(bytes: ByteArray): Any? =
        decodeElement(bytes).toPlainValue()

    /**
     * Decodes a `"bplist00"` binary property list directly into an instance of [targetClass].
     */
    fun <T : Any> decodeAs(
        bytes: ByteArray,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        if (!isBinaryPlist(bytes)) return defaultValue
        val element = runCatching { decodeElement(bytes) }.getOrNull() ?: return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    inline fun <reified T : Any> decodeAs(
        bytes: ByteArray,
        defaultValue: T? = null
    ): T? = decodeAs(bytes, T::class, defaultValue)

    private sealed class PlistNode {
        data object NullNode : PlistNode()
        data class BoolNode(val value: Boolean) : PlistNode()
        data class IntNode(val value: Long, val byteCount: Int) : PlistNode()
        data class FloatNode(val value: Float) : PlistNode()
        data class DoubleNode(val value: Double) : PlistNode()
        data class StringNode(val value: String) : PlistNode()
        data class ArrayNode(val elementRefs: IntArray) : PlistNode()
        data class DictNode(val keyRefs: IntArray, val valueRefs: IntArray) : PlistNode()
    }

    private fun flattenElement(element: PreferenceElement, out: MutableList<PlistNode>): Int {
        val myIndex = out.size
        // Reserve slot so parent index precedes children (topObjectIndex = 0)
        out.add(PlistNode.NullNode)

        val node: PlistNode = when (element.kind) {
            PreferenceElementKind.NULL -> PlistNode.NullNode
            PreferenceElementKind.BOOLEAN -> PlistNode.BoolNode(element.booleanValue == true)
            PreferenceElementKind.NUMBER -> {
                val n = element.numberValue ?: 0
                when (element.numberTypeTag) {
                    PreferenceElement.NUMBER_TAG_BYTE -> PlistNode.IntNode(n.toLong(), 1)
                    PreferenceElement.NUMBER_TAG_SHORT -> PlistNode.IntNode(n.toLong(), 2)
                    PreferenceElement.NUMBER_TAG_INT -> PlistNode.IntNode(n.toLong(), 4)
                    PreferenceElement.NUMBER_TAG_LONG -> PlistNode.IntNode(n.toLong(), 8)
                    PreferenceElement.NUMBER_TAG_FLOAT -> PlistNode.FloatNode(n.toFloat())
                    PreferenceElement.NUMBER_TAG_DOUBLE -> PlistNode.DoubleNode(n.toDouble())
                    else -> PlistNode.DoubleNode(n.toDouble())
                }
            }
            PreferenceElementKind.STRING -> PlistNode.StringNode(element.stringValue ?: "")
            PreferenceElementKind.ARRAY -> {
                val refs = IntArray(element.arrayElements.size)
                for (i in element.arrayElements.indices) {
                    refs[i] = flattenElement(element.arrayElements[i], out)
                }
                PlistNode.ArrayNode(refs)
            }
            PreferenceElementKind.MAP, PreferenceElementKind.OBJECT -> {
                val size = element.mapEntries.size
                val keyRefs = IntArray(size)
                val valRefs = IntArray(size)
                var idx = 0
                for ((k, v) in element.mapEntries) {
                    val kIdx = out.size
                    out.add(PlistNode.StringNode(k))
                    keyRefs[idx] = kIdx
                    valRefs[idx] = flattenElement(v, out)
                    idx++
                }
                PlistNode.DictNode(keyRefs, valRefs)
            }
        }

        out[myIndex] = node
        return myIndex
    }

    private fun writePlistNode(out: ByteWriter, node: PlistNode, objectRefSize: Int) {
        when (node) {
            is PlistNode.NullNode -> out.writeByte(0x00)
            is PlistNode.BoolNode -> out.writeByte(if (node.value) 0x09 else 0x08)
            is PlistNode.IntNode -> {
                when (node.byteCount) {
                    1 -> {
                        out.writeByte(0x10)
                        out.writeByte(node.value.toByte())
                    }
                    2 -> {
                        out.writeByte(0x11)
                        out.writeShort(node.value.toShort())
                    }
                    4 -> {
                        out.writeByte(0x12)
                        out.writeInt(node.value.toInt())
                    }
                    else -> {
                        out.writeByte(0x13)
                        out.writeLong(node.value)
                    }
                }
            }
            is PlistNode.FloatNode -> {
                out.writeByte(0x22)
                out.writeInt(node.value.toRawBits())
            }
            is PlistNode.DoubleNode -> {
                out.writeByte(0x23)
                out.writeLong(node.value.toRawBits())
            }
            is PlistNode.StringNode -> {
                val s = node.value
                val isAscii = s.all { it.code in 0..0x7F }
                if (isAscii) {
                    writeMarkerAndLength(out, 0x50, s.length)
                    for (ch in s) {
                        out.writeByte(ch.code.toByte())
                    }
                } else {
                    writeMarkerAndLength(out, 0x60, s.length)
                    for (ch in s) {
                        out.writeShort(ch.code.toShort())
                    }
                }
            }
            is PlistNode.ArrayNode -> {
                writeMarkerAndLength(out, 0xA0, node.elementRefs.size)
                for (ref in node.elementRefs) {
                    out.writeUnsignedInt(ref, objectRefSize)
                }
            }
            is PlistNode.DictNode -> {
                writeMarkerAndLength(out, 0xD0, node.keyRefs.size)
                for (kRef in node.keyRefs) {
                    out.writeUnsignedInt(kRef, objectRefSize)
                }
                for (vRef in node.valueRefs) {
                    out.writeUnsignedInt(vRef, objectRefSize)
                }
            }
        }
    }

    private fun writeMarkerAndLength(out: ByteWriter, highNibble: Int, count: Int) {
        if (count < 15) {
            out.writeByte((highNibble or count).toByte())
        } else {
            out.writeByte((highNibble or 0x0F).toByte())
            when {
                count < 256 -> {
                    out.writeByte(0x10)
                    out.writeByte(count.toByte())
                }
                count < 65536 -> {
                    out.writeByte(0x11)
                    out.writeShort(count.toShort())
                }
                else -> {
                    out.writeByte(0x12)
                    out.writeInt(count)
                }
            }
        }
    }

    private fun decodeObjectAtIndex(
        bytes: ByteArray,
        offsets: IntArray,
        objectRefSize: Int,
        index: Int,
        visiting: MutableSet<Int>
    ): PreferenceElement {
        if (index !in offsets.indices) return PreferenceElement.Null
        if (!visiting.add(index)) return PreferenceElement.Null

        try {
            val pos = offsets[index]
            if (pos < 8 || pos >= bytes.size - 32) return PreferenceElement.Null

            val marker = bytes[pos].toInt() and 0xFF
            val high = marker and 0xF0
            val low = marker and 0x0F

            return when (high) {
                0x00 -> when (marker) {
                    0x08 -> PreferenceElement.ofBoolean(false)
                    0x09 -> PreferenceElement.ofBoolean(true)
                    else -> PreferenceElement.Null
                }
                0x10 -> {
                    val byteCount = 1 shl low
                    val payloadPos = pos + 1
                    when (byteCount) {
                        1 -> PreferenceElement.ofByte(bytes[payloadPos])
                        2 -> PreferenceElement.ofShort(readShortBE(bytes, payloadPos))
                        4 -> PreferenceElement.ofInt(readIntBE(bytes, payloadPos))
                        8 -> PreferenceElement.ofLong(readLongBE(bytes, payloadPos))
                        else -> PreferenceElement.ofLong(readLongBE(bytes, payloadPos))
                    }
                }
                0x20 -> {
                    val payloadPos = pos + 1
                    when (low) {
                        2 -> PreferenceElement.ofFloat(Float.fromBits(readIntBE(bytes, payloadPos)))
                        3 -> PreferenceElement.ofDouble(Double.fromBits(readLongBE(bytes, payloadPos)))
                        else -> PreferenceElement.ofDouble(0.0)
                    }
                }
                0x40 -> {
                    val (count, dataStart) = readLengthAndPayloadOffset(bytes, pos, low)
                    val list = ArrayList<PreferenceElement>(count)
                    for (i in 0 until count) {
                        if (dataStart + i < bytes.size) {
                            list.add(PreferenceElement.ofByte(bytes[dataStart + i]))
                        }
                    }
                    PreferenceElement.ofArray(list)
                }
                0x50 -> {
                    val (count, strStart) = readLengthAndPayloadOffset(bytes, pos, low)
                    val sb = StringBuilder(count)
                    for (i in 0 until count) {
                        if (strStart + i < bytes.size) {
                            sb.append((bytes[strStart + i].toInt() and 0xFF).toChar())
                        }
                    }
                    PreferenceElement.ofString(sb.toString())
                }
                0x60 -> {
                    val (charCount, strStart) = readLengthAndPayloadOffset(bytes, pos, low)
                    val sb = StringBuilder(charCount)
                    for (i in 0 until charCount) {
                        val charPos = strStart + i * 2
                        if (charPos + 1 < bytes.size) {
                            val codeUnit = readShortBE(bytes, charPos).toInt() and 0xFFFF
                            sb.append(codeUnit.toChar())
                        }
                    }
                    PreferenceElement.ofString(sb.toString())
                }
                0xA0 -> {
                    val (count, refsStart) = readLengthAndPayloadOffset(bytes, pos, low)
                    val list = ArrayList<PreferenceElement>(count)
                    for (i in 0 until count) {
                        val refPos = refsStart + i * objectRefSize
                        val childIdx = readUnsignedIntBE(bytes, refPos, objectRefSize)
                        list.add(decodeObjectAtIndex(bytes, offsets, objectRefSize, childIdx, visiting))
                    }
                    PreferenceElement.ofArray(list)
                }
                0xD0 -> {
                    val (count, refsStart) = readLengthAndPayloadOffset(bytes, pos, low)
                    val valRefsStart = refsStart + count * objectRefSize
                    val entries = LinkedHashMap<String, PreferenceElement>(count)
                    for (i in 0 until count) {
                        val kIdx = readUnsignedIntBE(bytes, refsStart + i * objectRefSize, objectRefSize)
                        val vIdx = readUnsignedIntBE(bytes, valRefsStart + i * objectRefSize, objectRefSize)
                        val keyElem = decodeObjectAtIndex(bytes, offsets, objectRefSize, kIdx, visiting)
                        val valElem = decodeObjectAtIndex(bytes, offsets, objectRefSize, vIdx, visiting)
                        val keyStr = keyElem.asString("") ?: ""
                        entries[keyStr] = valElem
                    }
                    PreferenceElement.ofObject(entries)
                }
                else -> PreferenceElement.Null
            }
        } finally {
            visiting.remove(index)
        }
    }

    private fun readLengthAndPayloadOffset(bytes: ByteArray, markerPos: Int, lowNibble: Int): Pair<Int, Int> {
        if (lowNibble < 0x0F) {
            return lowNibble to (markerPos + 1)
        }
        val intMarkerPos = markerPos + 1
        if (intMarkerPos >= bytes.size) return 0 to intMarkerPos
        val intMarker = bytes[intMarkerPos].toInt() and 0xFF
        val byteCount = 1 shl (intMarker and 0x0F)
        val countPos = intMarkerPos + 1
        val count = readUnsignedIntBE(bytes, countPos, byteCount)
        return count to (countPos + byteCount)
    }

    private fun readUnsignedIntBE(bytes: ByteArray, offset: Int, byteCount: Int): Int {
        var result = 0
        for (i in 0 until byteCount) {
            val b = if (offset + i in bytes.indices) (bytes[offset + i].toInt() and 0xFF) else 0
            result = (result shl 8) or b
        }
        return result
    }

    private fun readShortBE(bytes: ByteArray, offset: Int): Short {
        val b0 = bytes.getOrElse(offset) { 0 }.toInt() and 0xFF
        val b1 = bytes.getOrElse(offset + 1) { 0 }.toInt() and 0xFF
        return ((b0 shl 8) or b1).toShort()
    }

    private fun readIntBE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes.getOrElse(offset) { 0 }.toInt() and 0xFF
        val b1 = bytes.getOrElse(offset + 1) { 0 }.toInt() and 0xFF
        val b2 = bytes.getOrElse(offset + 2) { 0 }.toInt() and 0xFF
        val b3 = bytes.getOrElse(offset + 3) { 0 }.toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readLongBE(bytes: ByteArray, offset: Int): Long {
        val hi = readIntBE(bytes, offset).toLong() and 0xFFFFFFFFL
        val lo = readIntBE(bytes, offset + 4).toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    private class ByteWriter(initialCapacity: Int = 128) {
        private var buf = ByteArray(initialCapacity)
        var size: Int = 0
            private set

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

        fun writeBytes(bytes: ByteArray) {
            ensureCapacity(bytes.size)
            bytes.copyInto(buf, destinationOffset = size)
            size += bytes.size
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

        fun writeUnsignedInt(value: Int, byteCount: Int) {
            ensureCapacity(byteCount)
            for (shift in (byteCount - 1) downTo 0) {
                buf[size++] = ((value ushr (shift * 8)) and 0xFF).toByte()
            }
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
