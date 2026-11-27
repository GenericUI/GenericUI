package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.DataClassCodecRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.io.WritableByteBuffer
import kotlin.reflect.KClass

/**
 * Cross-platform Apple Binary Property List (`bplist00`) encoder and decoder
 * (`com.nexustools.genericui.util.BinaryPlist`).
 *
 * Operates directly on [Writable] and [Readable] streams (automatically supporting [WritableByteBuffer],
 * [com.nexustools.genericui.io.ReadableByteBuffer], file streams, network sockets, HTTP streams, and database streams).
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
 *    - `0x4n`: `NSData` (`ByteArray`: `n` raw bytes, or `0x4F` + int count + raw bytes)
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
     * Returns `true` if [buffer] begins with the 8-byte `"bplist00"` header and includes a 32-byte trailer.
     */
    fun isBinaryPlist(buffer: ByteBuffer): Boolean {
        if (buffer.size < 40) return false
        for (i in 0 until 8) {
            if (buffer.readByte(i) != HEADER_BYTES[i]) return false
        }
        return true
    }

    /**
     * Encodes a [PreferenceElement] tree in Apple `"bplist00"` format directly into any [Writable] stream
     * (including [WritableByteBuffer]).
     */
    fun encodeElement(element: PreferenceElement, writable: Writable) {
        val objects = ArrayList<PlistNode>()
        flattenElement(element, objects)

        val numObjects = objects.size
        val objectRefSize = when {
            numObjects < 256 -> 1
            numObjects < 65536 -> 2
            else -> 4
        }

        val baseOffset = writable.bytesWritten
        writable.writeBytes(HEADER_BYTES)

        val offsets = IntArray(numObjects)
        for (i in 0 until numObjects) {
            offsets[i] = (writable.bytesWritten - baseOffset).toInt()
            writePlistNode(writable, objects[i], objectRefSize)
        }

        val offsetTableStart = (writable.bytesWritten - baseOffset).toInt()
        val maxOffset = if (numObjects > 0) offsets.maxOrNull() ?: 0 else 0
        val offsetIntSize = when {
            maxOffset < 256 -> 1
            maxOffset < 65536 -> 2
            else -> 4
        }

        for (offset in offsets) {
            writable.writeUnsignedInt(offset, offsetIntSize, ByteOrder.BIG_ENDIAN)
        }

        // 32-byte trailer
        repeat(6) { writable.writeByte(0) }
        writable.writeByte(offsetIntSize.toByte())
        writable.writeByte(objectRefSize.toByte())
        writable.writeLong(numObjects.toLong(), ByteOrder.BIG_ENDIAN)
        writable.writeLong(0L, ByteOrder.BIG_ENDIAN) // topObjectIndex = 0
        writable.writeLong(offsetTableStart.toLong(), ByteOrder.BIG_ENDIAN)
    }

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` in `"bplist00"` format
     * directly into any [Writable] stream.
     */
    fun encode(value: Any?, writable: Writable) {
        encodeElement(PreferenceObjectCodec.toElement(value), writable)
    }

    /**
     * Encodes a [PreferenceElement] tree into a big-endian [ByteBuffer] in Apple `"bplist00"` format.
     */
    fun encodeElementToBuffer(element: PreferenceElement): ByteBuffer =
        WritableByteBuffer(initialCapacity = 128, byteOrder = ByteOrder.BIG_ENDIAN)
            .also { encodeElement(element, it) }
            .toByteBuffer()

    /**
     * Encodes a [PreferenceElement] tree into standard Apple `"bplist00"` binary property list bytes.
     */
    fun encodeElement(element: PreferenceElement): ByteArray =
        WritableByteBuffer(initialCapacity = 128, byteOrder = ByteOrder.BIG_ENDIAN)
            .also { encodeElement(element, it) }
            .toByteArray()

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into `"bplist00"` binary bytes.
     */
    fun encode(value: Any?): ByteArray =
        encodeElement(PreferenceObjectCodec.toElement(value))

    /**
     * Encodes any object, [Map], [Collection], array, primitive, or `null` into a `"bplist00"` [ByteBuffer].
     */
    fun encodeToBuffer(value: Any?): ByteBuffer =
        encodeElementToBuffer(PreferenceObjectCodec.toElement(value))

    /**
     * Encodes a data class (or registered `@BinarySerializable` / reflected object) [value]
     * of [targetClass] into Apple `"bplist00"` binary bytes.
     */
    fun <T : Any> encodeDataClass(value: T, targetClass: KClass<T>): ByteArray {
        val element = DataClassCodecRegistry.encode(value, targetClass)
            ?: PreferenceObjectCodec.toElement(value)
        return encodeElement(element)
    }

    inline fun <reified T : Any> encodeDataClass(value: T): ByteArray =
        encodeDataClass(value, T::class)

    /**
     * Decodes Apple `"bplist00"` binary bytes directly into an instance of [targetClass] using
     * [DataClassCodecRegistry] or platform reflection, returning `null` if invalid or `null`.
     */
    fun <T : Any> decodeDataClass(bytes: ByteArray, targetClass: KClass<T>): T? {
        val element = decodeElementOrNull(bytes) ?: return null
        if (element.isNull) return null
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue = null)
    }

    inline fun <reified T : Any> decodeDataClass(bytes: ByteArray): T? =
        decodeDataClass(bytes, T::class)

    /**
     * Asynchronously decodes a `"bplist00"` binary property list from any [Readable] stream
     * (including [com.nexustools.genericui.io.ReadableByteBuffer]) into a [PreferenceElement] tree.
     */
    suspend fun decodeElement(readable: Readable): PreferenceElement =
        decodeElement(readable.readAllBuffer())

    /**
     * Asynchronously decodes a `"bplist00"` binary property list from any [Readable] stream into
     * standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    suspend fun decode(readable: Readable): Any? =
        decodeElement(readable).toPlainValue()

    /**
     * Asynchronously decodes a `"bplist00"` binary property list from any [Readable] stream
     * directly into an instance of [targetClass].
     */
    suspend fun <T : Any> decodeAs(
        readable: Readable,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        val buf = runCatching { readable.readAllBuffer() }.getOrNull() ?: return defaultValue
        return decodeAs(buf, targetClass, defaultValue)
    }

    suspend inline fun <reified T : Any> decodeAs(
        readable: Readable,
        defaultValue: T? = null
    ): T? = decodeAs(readable, T::class, defaultValue)

    /**
     * Strictly decodes a `"bplist00"` binary property list [ByteArray] into a [PreferenceElement] tree,
     * returning `null` if the input is not a valid `bplist00` file, has invalid trailer values,
     * out-of-bounds object offsets, cyclic object references, or truncated/corrupt payloads.
     *
     * Note: A valid `bplist00` file encoding `0x00` returns [PreferenceElement.Null] (non-null).
     */
    fun decodeElementOrNull(bytes: ByteArray): PreferenceElement? {
        if (!isBinaryPlist(bytes)) return null
        return decodeElementOrNull(ByteBuffer.wrap(bytes, byteOrder = ByteOrder.BIG_ENDIAN))
    }

    /**
     * Strictly decodes a `"bplist00"` binary property list [ByteBuffer] into a [PreferenceElement] tree,
     * returning `null` on invalid, corrupt, cyclic, or truncated inputs.
     */
    fun decodeElementOrNull(buffer: ByteBuffer): PreferenceElement? {
        if (!isBinaryPlist(buffer)) return null
        return try {
            val trailerPos = buffer.size - 32
            if (trailerPos < 8) return null
            val offsetIntSize = buffer.readUByte(trailerPos + 6).toInt()
            val objectRefSize = buffer.readUByte(trailerPos + 7).toInt()
            val numObjectsLong = buffer.readLong(trailerPos + 8, ByteOrder.BIG_ENDIAN)
            val topObjectIndexLong = buffer.readLong(trailerPos + 16, ByteOrder.BIG_ENDIAN)
            val offsetTableStartLong = buffer.readLong(trailerPos + 24, ByteOrder.BIG_ENDIAN)

            if (offsetIntSize !in setOf(1, 2, 4, 8) || objectRefSize !in setOf(1, 2, 4, 8)) {
                return null
            }
            if (offsetTableStartLong < 8L || offsetTableStartLong >= trailerPos.toLong()) {
                return null
            }
            val maxOffsets = (trailerPos.toLong() - offsetTableStartLong) / offsetIntSize.toLong()
            if (numObjectsLong <= 0L || numObjectsLong > maxOffsets || numObjectsLong > Int.MAX_VALUE.toLong()) {
                return null
            }
            val numObjects = numObjectsLong.toInt()
            val offsetTableStart = offsetTableStartLong.toInt()
            if (topObjectIndexLong !in 0L until numObjectsLong) {
                return null
            }

            val offsets = IntArray(numObjects)
            for (i in 0 until numObjects) {
                val pos = offsetTableStart + i * offsetIntSize
                val objOffset = readUnsignedLong(buffer, pos, offsetIntSize)
                if (objOffset < 8L || objOffset >= offsetTableStartLong) {
                    return null
                }
                offsets[i] = objOffset.toInt()
            }

            decodeObjectAtIndexOrNull(
                buffer = buffer,
                offsets = offsets,
                offsetTableStart = offsetTableStart,
                objectRefSize = objectRefSize,
                index = topObjectIndexLong.toInt(),
                visiting = mutableSetOf()
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes a `"bplist00"` binary property list [ByteArray] into a [PreferenceElement] tree.
     */
    fun decodeElement(bytes: ByteArray): PreferenceElement =
        decodeElementOrNull(bytes) ?: PreferenceElement.Null

    /**
     * Decodes a `"bplist00"` binary property list [ByteBuffer] into a [PreferenceElement] tree.
     */
    fun decodeElement(buffer: ByteBuffer): PreferenceElement =
        decodeElementOrNull(buffer) ?: PreferenceElement.Null

    /**
     * Decodes a `"bplist00"` binary property list into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(bytes: ByteArray): Any? =
        decodeElement(bytes).toPlainValue()

    /**
     * Decodes a `"bplist00"` [ByteBuffer] into standard Kotlin primitives, [List]s, and [LinkedHashMap]s.
     */
    fun decode(buffer: ByteBuffer): Any? =
        decodeElement(buffer).toPlainValue()

    /**
     * Decodes a `"bplist00"` binary property list directly into an instance of [targetClass].
     */
    fun <T : Any> decodeAs(
        bytes: ByteArray,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        val element = decodeElementOrNull(bytes) ?: return defaultValue
        return PreferenceObjectCodec.fromElement(element, targetClass, defaultValue)
    }

    /**
     * Decodes a `"bplist00"` [ByteBuffer] directly into an instance of [targetClass].
     */
    fun <T : Any> decodeAs(
        buffer: ByteBuffer,
        targetClass: KClass<T>,
        defaultValue: T? = null
    ): T? {
        val element = decodeElementOrNull(buffer) ?: return defaultValue
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

    private sealed class PlistNode {
        data object NullNode : PlistNode()
        data class BoolNode(val value: Boolean) : PlistNode()
        data class IntNode(val value: Long, val byteCount: Int) : PlistNode()
        data class FloatNode(val value: Float) : PlistNode()
        data class DoubleNode(val value: Double) : PlistNode()
        data class DataNode(val bytes: ByteArray) : PlistNode() {
            override fun equals(other: Any?): Boolean =
                this === other || (other is DataNode && bytes.contentEquals(other.bytes))
            override fun hashCode(): Int = bytes.contentHashCode()
        }
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
            PreferenceElementKind.BYTES -> PlistNode.DataNode(element.bytesOrNull() ?: ByteArray(0))
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

    private fun writePlistNode(out: Writable, node: PlistNode, objectRefSize: Int) {
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
                        out.writeShort(node.value.toShort(), ByteOrder.BIG_ENDIAN)
                    }
                    4 -> {
                        out.writeByte(0x12)
                        out.writeInt(node.value.toInt(), ByteOrder.BIG_ENDIAN)
                    }
                    else -> {
                        out.writeByte(0x13)
                        out.writeLong(node.value, ByteOrder.BIG_ENDIAN)
                    }
                }
            }
            is PlistNode.FloatNode -> {
                out.writeByte(0x22)
                out.writeFloat(node.value, ByteOrder.BIG_ENDIAN)
            }
            is PlistNode.DoubleNode -> {
                out.writeByte(0x23)
                out.writeDouble(node.value, ByteOrder.BIG_ENDIAN)
            }
            is PlistNode.DataNode -> {
                writeMarkerAndLength(out, 0x40, node.bytes.size)
                if (node.bytes.isNotEmpty()) {
                    out.writeBytes(node.bytes, 0, node.bytes.size)
                }
            }
            is PlistNode.StringNode -> {
                val s = node.value
                val isAscii = s.all { it.code in 0..0x7F }
                if (isAscii) {
                    writeMarkerAndLength(out, 0x50, s.length)
                    out.writeString(s, "ascii")
                } else {
                    writeMarkerAndLength(out, 0x60, s.length)
                    out.writeString(s, "utf16be")
                }
            }
            is PlistNode.ArrayNode -> {
                writeMarkerAndLength(out, 0xA0, node.elementRefs.size)
                for (ref in node.elementRefs) {
                    out.writeUnsignedInt(ref, objectRefSize, ByteOrder.BIG_ENDIAN)
                }
            }
            is PlistNode.DictNode -> {
                writeMarkerAndLength(out, 0xD0, node.keyRefs.size)
                for (kRef in node.keyRefs) {
                    out.writeUnsignedInt(kRef, objectRefSize, ByteOrder.BIG_ENDIAN)
                }
                for (vRef in node.valueRefs) {
                    out.writeUnsignedInt(vRef, objectRefSize, ByteOrder.BIG_ENDIAN)
                }
            }
        }
    }

    private fun writeMarkerAndLength(out: Writable, highNibble: Int, count: Int) {
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
                    out.writeShort(count.toShort(), ByteOrder.BIG_ENDIAN)
                }
                else -> {
                    out.writeByte(0x12)
                    out.writeInt(count, ByteOrder.BIG_ENDIAN)
                }
            }
        }
    }

    private fun decodeObjectAtIndexOrNull(
        buffer: ByteBuffer,
        offsets: IntArray,
        offsetTableStart: Int,
        objectRefSize: Int,
        index: Int,
        visiting: MutableSet<Int>
    ): PreferenceElement? {
        if (index !in offsets.indices) return null
        if (!visiting.add(index)) return null

        try {
            val pos = offsets[index]
            if (pos < 8 || pos >= offsetTableStart) return null

            val marker = buffer.readUByte(pos).toInt()
            val high = marker and 0xF0
            val low = marker and 0x0F

            return when (high) {
                0x00 -> when (marker) {
                    0x00 -> PreferenceElement.Null
                    0x08 -> PreferenceElement.ofBoolean(false)
                    0x09 -> PreferenceElement.ofBoolean(true)
                    else -> null
                }
                0x10 -> {
                    if (low > 3) return null
                    val byteCount = 1 shl low
                    val payloadPos = pos + 1
                    if (payloadPos + byteCount > offsetTableStart) return null
                    when (byteCount) {
                        1 -> PreferenceElement.ofByte(buffer.readByte(payloadPos))
                        2 -> PreferenceElement.ofShort(buffer.readShort(payloadPos, ByteOrder.BIG_ENDIAN))
                        4 -> PreferenceElement.ofInt(buffer.readInt(payloadPos, ByteOrder.BIG_ENDIAN))
                        8 -> PreferenceElement.ofLong(buffer.readLong(payloadPos, ByteOrder.BIG_ENDIAN))
                        else -> null
                    }
                }
                0x20 -> {
                    if (low != 2 && low != 3) return null
                    val byteCount = 1 shl low
                    val payloadPos = pos + 1
                    if (payloadPos + byteCount > offsetTableStart) return null
                    when (low) {
                        2 -> PreferenceElement.ofFloat(buffer.readFloat(payloadPos, ByteOrder.BIG_ENDIAN))
                        3 -> PreferenceElement.ofDouble(buffer.readDouble(payloadPos, ByteOrder.BIG_ENDIAN))
                        else -> null
                    }
                }
                0x40 -> {
                    val (count, dataStart) = readLengthAndPayloadOffsetOrNull(buffer, pos, low, offsetTableStart)
                        ?: return null
                    if (count < 0 || dataStart.toLong() + count.toLong() > offsetTableStart.toLong()) return null
                    PreferenceElement.ofBytes(buffer.toByteArray(dataStart, dataStart + count))
                }
                0x50 -> {
                    val (count, strStart) = readLengthAndPayloadOffsetOrNull(buffer, pos, low, offsetTableStart)
                        ?: return null
                    if (count < 0 || strStart.toLong() + count.toLong() > offsetTableStart.toLong()) return null
                    PreferenceElement.ofString(buffer.toString("ascii", strStart, strStart + count))
                }
                0x60 -> {
                    val (charCount, strStart) = readLengthAndPayloadOffsetOrNull(buffer, pos, low, offsetTableStart)
                        ?: return null
                    if (charCount < 0 || strStart.toLong() + charCount.toLong() * 2L > offsetTableStart.toLong()) {
                        return null
                    }
                    PreferenceElement.ofString(buffer.toString("utf16be", strStart, strStart + charCount * 2))
                }
                0xA0 -> {
                    val (count, refsStart) = readLengthAndPayloadOffsetOrNull(buffer, pos, low, offsetTableStart)
                        ?: return null
                    if (count < 0 || refsStart.toLong() + count.toLong() * objectRefSize.toLong() > offsetTableStart.toLong()) {
                        return null
                    }
                    val list = ArrayList<PreferenceElement>(count)
                    for (i in 0 until count) {
                        val refPos = refsStart + i * objectRefSize
                        val childIdx = readUnsignedLong(buffer, refPos, objectRefSize)
                        if (childIdx !in 0L until offsets.size.toLong()) return null
                        val child = decodeObjectAtIndexOrNull(
                            buffer,
                            offsets,
                            offsetTableStart,
                            objectRefSize,
                            childIdx.toInt(),
                            visiting
                        ) ?: return null
                        list.add(child)
                    }
                    PreferenceElement.ofArray(list)
                }
                0xD0 -> {
                    val (count, refsStart) = readLengthAndPayloadOffsetOrNull(buffer, pos, low, offsetTableStart)
                        ?: return null
                    val neededBytes = count.toLong() * 2L * objectRefSize.toLong()
                    if (count < 0 || refsStart.toLong() + neededBytes > offsetTableStart.toLong()) return null
                    val valRefsStart = refsStart + count * objectRefSize
                    val entries = LinkedHashMap<String, PreferenceElement>(count)
                    for (i in 0 until count) {
                        val kIdx = readUnsignedLong(buffer, refsStart + i * objectRefSize, objectRefSize)
                        val vIdx = readUnsignedLong(buffer, valRefsStart + i * objectRefSize, objectRefSize)
                        if (kIdx !in 0L until offsets.size.toLong() || vIdx !in 0L until offsets.size.toLong()) {
                            return null
                        }
                        val keyElem = decodeObjectAtIndexOrNull(
                            buffer,
                            offsets,
                            offsetTableStart,
                            objectRefSize,
                            kIdx.toInt(),
                            visiting
                        ) ?: return null
                        if (!keyElem.isString) return null
                        val valElem = decodeObjectAtIndexOrNull(
                            buffer,
                            offsets,
                            offsetTableStart,
                            objectRefSize,
                            vIdx.toInt(),
                            visiting
                        ) ?: return null
                        entries[keyElem.stringValue ?: ""] = valElem
                    }
                    PreferenceElement.ofObject(entries)
                }
                else -> null
            }
        } finally {
            visiting.remove(index)
        }
    }

    private fun readUnsignedLong(buffer: ByteBuffer, offset: Int, byteCount: Int): Long =
        when (byteCount) {
            8 -> buffer.readLong(offset, ByteOrder.BIG_ENDIAN)
            4 -> buffer.readUnsignedInt(offset, 4, ByteOrder.BIG_ENDIAN).toLong() and 0xFFFFFFFFL
            else -> buffer.readUnsignedInt(offset, byteCount, ByteOrder.BIG_ENDIAN).toLong()
        }

    private fun readLengthAndPayloadOffsetOrNull(
        buffer: ByteBuffer,
        markerPos: Int,
        lowNibble: Int,
        offsetTableStart: Int
    ): Pair<Int, Int>? {
        if (lowNibble < 0x0F) {
            val payloadPos = markerPos + 1
            if (payloadPos > offsetTableStart) return null
            return lowNibble to payloadPos
        }
        val intMarkerPos = markerPos + 1
        if (intMarkerPos >= offsetTableStart) return null
        val intMarker = buffer.readUByte(intMarkerPos).toInt()
        if ((intMarker and 0xF0) != 0x10) return null
        val intLow = intMarker and 0x0F
        if (intLow > 3) return null
        val byteCount = 1 shl intLow
        val countPos = intMarkerPos + 1
        if (countPos + byteCount > offsetTableStart) return null
        val countLong = readUnsignedLong(buffer, countPos, byteCount)
        if (countLong < 0L || countLong > Int.MAX_VALUE.toLong()) return null
        return countLong.toInt() to (countPos + byteCount)
    }
}
