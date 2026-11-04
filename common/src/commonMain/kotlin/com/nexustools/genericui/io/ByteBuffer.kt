package com.nexustools.genericui.io

/**
 * Cross-platform binary byte buffer used by [Readable], [Writable], [FileSystem],
 * [com.nexustools.genericui.util.UserDirectory], [com.nexustools.genericui.util.ConfigDirectory],
 * and [com.nexustools.genericui.util.CacheDirectory].
 *
 * Wraps a backing [ByteArray] with a cursor ([position]), [limit], [capacity], and [byteOrder],
 * and provides both indexed (`readInt(index)` / `writeInt(index, value)`) and cursor-advancing
 * (`readInt()` / `writeInt(value)`) primitive readers and writers in [ByteOrder.LITTLE_ENDIAN]
 * or [ByteOrder.BIG_ENDIAN].
 */
open class ByteBuffer(
    protected var backing: ByteArray,
    position: Int = 0,
    limit: Int = backing.size,
    var byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) {
    var position: Int = position.coerceIn(0, backing.size)
        set(value) {
            field = value.coerceIn(0, this.limit)
        }

    var limit: Int = limit.coerceIn(0, backing.size)
        set(value) {
            field = value.coerceIn(0, backing.size)
            if (position > field) {
                position = field
            }
        }

    val capacity: Int
        get() = backing.size

    val size: Int
        get() = limit

    val remaining: Int
        get() = (limit - position).coerceAtLeast(0)

    val hasRemaining: Boolean
        get() = position < limit

    val isEmpty: Boolean
        get() = limit == 0

    fun isNotEmpty(): Boolean = limit > 0

    open fun clear(): ByteBuffer {
        position = 0
        limit = backing.size
        return this
    }

    open fun flip(): ByteBuffer {
        limit = position
        position = 0
        return this
    }

    open fun rewind(): ByteBuffer {
        position = 0
        return this
    }

    open fun compact(): ByteBuffer {
        val rem = remaining
        if (rem > 0 && position > 0) {
            backing.copyInto(backing, destinationOffset = 0, startIndex = position, endIndex = limit)
        }
        position = rem
        limit = backing.size
        return this
    }

    open fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= backing.size) return
        var newCap = maxOf(backing.size, 16)
        while (newCap < minCapacity) {
            newCap *= 2
        }
        backing = backing.copyOf(newCap)
    }

    operator fun get(index: Int): Byte = readByte(index)

    operator fun set(index: Int, value: Byte) {
        writeByte(index, value)
    }

    open fun get(): Byte {
        if (position >= limit) {
            throw IndexOutOfBoundsException("Buffer underflow at $position / $limit")
        }
        return backing[position++]
    }

    open fun get(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): ByteBuffer {
        val cleanOffset = offset.coerceIn(0, dst.size)
        val cleanLen = length.coerceIn(0, dst.size - cleanOffset)
        if (cleanLen > remaining) {
            throw IndexOutOfBoundsException("Buffer underflow: requested $cleanLen bytes, remaining $remaining")
        }
        if (cleanLen > 0) {
            backing.copyInto(dst, destinationOffset = cleanOffset, startIndex = position, endIndex = position + cleanLen)
            position += cleanLen
        }
        return this
    }

    open fun put(byte: Byte): ByteBuffer {
        if (position >= limit) {
            ensureCapacity(position + 1)
            limit = maxOf(limit, position + 1)
        }
        backing[position++] = byte
        return this
    }

    open fun put(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): ByteBuffer {
        val cleanOffset = offset.coerceIn(0, src.size)
        val cleanLen = length.coerceIn(0, src.size - cleanOffset)
        if (cleanLen == 0) return this
        if (position + cleanLen > backing.size) {
            ensureCapacity(position + cleanLen)
        }
        if (position + cleanLen > limit) {
            limit = position + cleanLen
        }
        src.copyInto(backing, destinationOffset = position, startIndex = cleanOffset, endIndex = cleanOffset + cleanLen)
        position += cleanLen
        return this
    }

    open fun put(src: ByteBuffer): ByteBuffer {
        val bytes = src.readBytes()
        return put(bytes, 0, bytes.size)
    }

    fun getByte(): Byte = get()

    open fun putByte(value: Byte): ByteBuffer = put(value)

    fun readBytes(maxLength: Int = remaining): ByteArray {
        val count = maxLength.coerceIn(0, remaining)
        if (count == 0) return ByteArray(0)
        val out = backing.copyOfRange(position, position + count)
        position += count
        return out
    }

    open fun writeBytes(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): ByteBuffer =
        put(src, offset, length)

    // =========================================================================
    // Indexed Primitive Read/Write (Does NOT move position)
    // =========================================================================

    open fun readByte(index: Int): Byte {
        if (index < 0 || index >= limit) {
            throw IndexOutOfBoundsException("Index $index out of bounds for limit $limit")
        }
        return backing[index]
    }

    open fun writeByte(index: Int, value: Byte): ByteBuffer {
        if (index < 0 || index >= limit) {
            throw IndexOutOfBoundsException("Index $index out of bounds for limit $limit")
        }
        backing[index] = value
        return this
    }

    open fun writeByte(index: Int, value: Int): ByteBuffer =
        writeByte(index, value.toByte())

    open fun readUByte(index: Int): UByte =
        readByte(index).toUByte()

    open fun writeUByte(index: Int, value: UByte): ByteBuffer =
        writeByte(index, value.toByte())

    open fun readBoolean(index: Int): Boolean =
        readByte(index).toInt() != 0

    open fun writeBoolean(index: Int, value: Boolean): ByteBuffer =
        writeByte(index, if (value) 1 else 0)

    open fun readShort(index: Int, order: ByteOrder = byteOrder): Short {
        val b0 = readByte(index).toInt() and 0xFF
        val b1 = readByte(index + 1).toInt() and 0xFF
        return if (order == ByteOrder.BIG_ENDIAN) {
            ((b0 shl 8) or b1).toShort()
        } else {
            ((b1 shl 8) or b0).toShort()
        }
    }

    open fun writeShort(index: Int, value: Short, order: ByteOrder = byteOrder): ByteBuffer {
        val v = value.toInt()
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(index, ((v ushr 8) and 0xFF).toByte())
            writeByte(index + 1, (v and 0xFF).toByte())
        } else {
            writeByte(index, (v and 0xFF).toByte())
            writeByte(index + 1, ((v ushr 8) and 0xFF).toByte())
        }
        return this
    }

    open fun readUShort(index: Int, order: ByteOrder = byteOrder): UShort =
        readShort(index, order).toUShort()

    open fun writeUShort(index: Int, value: UShort, order: ByteOrder = byteOrder): ByteBuffer =
        writeShort(index, value.toShort(), order)

    open fun readInt(index: Int, order: ByteOrder = byteOrder): Int {
        val b0 = readByte(index).toInt() and 0xFF
        val b1 = readByte(index + 1).toInt() and 0xFF
        val b2 = readByte(index + 2).toInt() and 0xFF
        val b3 = readByte(index + 3).toInt() and 0xFF
        return if (order == ByteOrder.BIG_ENDIAN) {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        } else {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
    }

    open fun writeInt(index: Int, value: Int, order: ByteOrder = byteOrder): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(index, ((value ushr 24) and 0xFF).toByte())
            writeByte(index + 1, ((value ushr 16) and 0xFF).toByte())
            writeByte(index + 2, ((value ushr 8) and 0xFF).toByte())
            writeByte(index + 3, (value and 0xFF).toByte())
        } else {
            writeByte(index, (value and 0xFF).toByte())
            writeByte(index + 1, ((value ushr 8) and 0xFF).toByte())
            writeByte(index + 2, ((value ushr 16) and 0xFF).toByte())
            writeByte(index + 3, ((value ushr 24) and 0xFF).toByte())
        }
        return this
    }

    open fun readUInt(index: Int, order: ByteOrder = byteOrder): UInt =
        readInt(index, order).toUInt()

    open fun writeUInt(index: Int, value: UInt, order: ByteOrder = byteOrder): ByteBuffer =
        writeInt(index, value.toInt(), order)

    open fun readLong(index: Int, order: ByteOrder = byteOrder): Long {
        return if (order == ByteOrder.BIG_ENDIAN) {
            val high = readInt(index, ByteOrder.BIG_ENDIAN).toLong() and 0xFFFFFFFFL
            val low = readInt(index + 4, ByteOrder.BIG_ENDIAN).toLong() and 0xFFFFFFFFL
            (high shl 32) or low
        } else {
            val low = readInt(index, ByteOrder.LITTLE_ENDIAN).toLong() and 0xFFFFFFFFL
            val high = readInt(index + 4, ByteOrder.LITTLE_ENDIAN).toLong() and 0xFFFFFFFFL
            (high shl 32) or low
        }
    }

    open fun writeLong(index: Int, value: Long, order: ByteOrder = byteOrder): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            writeInt(index, (value ushr 32).toInt(), ByteOrder.BIG_ENDIAN)
            writeInt(index + 4, (value and 0xFFFFFFFFL).toInt(), ByteOrder.BIG_ENDIAN)
        } else {
            writeInt(index, (value and 0xFFFFFFFFL).toInt(), ByteOrder.LITTLE_ENDIAN)
            writeInt(index + 4, (value ushr 32).toInt(), ByteOrder.LITTLE_ENDIAN)
        }
        return this
    }

    open fun readULong(index: Int, order: ByteOrder = byteOrder): ULong =
        readLong(index, order).toULong()

    open fun writeULong(index: Int, value: ULong, order: ByteOrder = byteOrder): ByteBuffer =
        writeLong(index, value.toLong(), order)

    open fun readFloat(index: Int, order: ByteOrder = byteOrder): Float =
        Float.fromBits(readInt(index, order))

    open fun writeFloat(index: Int, value: Float, order: ByteOrder = byteOrder): ByteBuffer =
        writeInt(index, value.toRawBits(), order)

    open fun readDouble(index: Int, order: ByteOrder = byteOrder): Double =
        Double.fromBits(readLong(index, order))

    open fun writeDouble(index: Int, value: Double, order: ByteOrder = byteOrder): ByteBuffer =
        writeLong(index, value.toRawBits(), order)

    open fun readUnsignedInt(index: Int, byteCount: Int, order: ByteOrder = byteOrder): Int {
        var result = 0
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until byteCount) {
                val b = if (index + i in 0 until limit) (readByte(index + i).toInt() and 0xFF) else 0
                result = (result shl 8) or b
            }
        } else {
            for (i in (byteCount - 1) downTo 0) {
                val b = if (index + i in 0 until limit) (readByte(index + i).toInt() and 0xFF) else 0
                result = (result shl 8) or b
            }
        }
        return result
    }

    open fun writeUnsignedInt(
        index: Int,
        value: Int,
        byteCount: Int,
        order: ByteOrder = byteOrder
    ): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            var shift = (byteCount - 1) * 8
            for (i in 0 until byteCount) {
                writeByte(index + i, ((value ushr shift) and 0xFF).toByte())
                shift -= 8
            }
        } else {
            for (i in 0 until byteCount) {
                writeByte(index + i, ((value ushr (i * 8)) and 0xFF).toByte())
            }
        }
        return this
    }

    // =========================================================================
    // Cursor-Advancing Primitive Read/Write at position
    // =========================================================================

    open fun readByte(): Byte = get()

    open fun writeByte(value: Byte): ByteBuffer = put(value)

    open fun writeByte(value: Int): ByteBuffer = put(value.toByte())

    open fun readUByte(): UByte = readByte().toUByte()

    open fun writeUByte(value: UByte): ByteBuffer = writeByte(value.toByte())

    open fun readBoolean(): Boolean = readByte().toInt() != 0

    open fun writeBoolean(value: Boolean): ByteBuffer = writeByte(if (value) 1 else 0)

    open fun readShort(order: ByteOrder = byteOrder): Short {
        val b0 = readByte().toInt() and 0xFF
        val b1 = readByte().toInt() and 0xFF
        return if (order == ByteOrder.BIG_ENDIAN) {
            ((b0 shl 8) or b1).toShort()
        } else {
            ((b1 shl 8) or b0).toShort()
        }
    }

    open fun writeShort(value: Short, order: ByteOrder = byteOrder): ByteBuffer {
        val v = value.toInt()
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(((v ushr 8) and 0xFF).toByte())
            writeByte((v and 0xFF).toByte())
        } else {
            writeByte((v and 0xFF).toByte())
            writeByte(((v ushr 8) and 0xFF).toByte())
        }
        return this
    }

    open fun readUShort(order: ByteOrder = byteOrder): UShort =
        readShort(order).toUShort()

    open fun writeUShort(value: UShort, order: ByteOrder = byteOrder): ByteBuffer =
        writeShort(value.toShort(), order)

    open fun readInt(order: ByteOrder = byteOrder): Int {
        val b0 = readByte().toInt() and 0xFF
        val b1 = readByte().toInt() and 0xFF
        val b2 = readByte().toInt() and 0xFF
        val b3 = readByte().toInt() and 0xFF
        return if (order == ByteOrder.BIG_ENDIAN) {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        } else {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
    }

    open fun writeInt(value: Int, order: ByteOrder = byteOrder): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            writeByte(((value ushr 24) and 0xFF).toByte())
            writeByte(((value ushr 16) and 0xFF).toByte())
            writeByte(((value ushr 8) and 0xFF).toByte())
            writeByte((value and 0xFF).toByte())
        } else {
            writeByte((value and 0xFF).toByte())
            writeByte(((value ushr 8) and 0xFF).toByte())
            writeByte(((value ushr 16) and 0xFF).toByte())
            writeByte(((value ushr 24) and 0xFF).toByte())
        }
        return this
    }

    open fun readUInt(order: ByteOrder = byteOrder): UInt =
        readInt(order).toUInt()

    open fun writeUInt(value: UInt, order: ByteOrder = byteOrder): ByteBuffer =
        writeInt(value.toInt(), order)

    open fun readLong(order: ByteOrder = byteOrder): Long {
        return if (order == ByteOrder.BIG_ENDIAN) {
            val high = readInt(ByteOrder.BIG_ENDIAN).toLong() and 0xFFFFFFFFL
            val low = readInt(ByteOrder.BIG_ENDIAN).toLong() and 0xFFFFFFFFL
            (high shl 32) or low
        } else {
            val low = readInt(ByteOrder.LITTLE_ENDIAN).toLong() and 0xFFFFFFFFL
            val high = readInt(ByteOrder.LITTLE_ENDIAN).toLong() and 0xFFFFFFFFL
            (high shl 32) or low
        }
    }

    open fun writeLong(value: Long, order: ByteOrder = byteOrder): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            writeInt((value ushr 32).toInt(), ByteOrder.BIG_ENDIAN)
            writeInt((value and 0xFFFFFFFFL).toInt(), ByteOrder.BIG_ENDIAN)
        } else {
            writeInt((value and 0xFFFFFFFFL).toInt(), ByteOrder.LITTLE_ENDIAN)
            writeInt((value ushr 32).toInt(), ByteOrder.LITTLE_ENDIAN)
        }
        return this
    }

    open fun readULong(order: ByteOrder = byteOrder): ULong =
        readLong(order).toULong()

    open fun writeULong(value: ULong, order: ByteOrder = byteOrder): ByteBuffer =
        writeLong(value.toLong(), order)

    open fun readFloat(order: ByteOrder = byteOrder): Float =
        Float.fromBits(readInt(order))

    open fun writeFloat(value: Float, order: ByteOrder = byteOrder): ByteBuffer =
        writeInt(value.toRawBits(), order)

    open fun readDouble(order: ByteOrder = byteOrder): Double =
        Double.fromBits(readLong(order))

    open fun writeDouble(value: Double, order: ByteOrder = byteOrder): ByteBuffer =
        writeLong(value.toRawBits(), order)

    open fun readUnsignedInt(byteCount: Int, order: ByteOrder = byteOrder): Int {
        val value = readUnsignedInt(position, byteCount, order)
        position = (position + byteCount).coerceAtMost(limit)
        return value
    }

    open fun writeUnsignedInt(value: Int, byteCount: Int, order: ByteOrder = byteOrder): ByteBuffer {
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in (byteCount - 1) downTo 0) {
                writeByte(((value ushr (i * 8)) and 0xFF).toByte())
            }
        } else {
            for (i in 0 until byteCount) {
                writeByte(((value ushr (i * 8)) and 0xFF).toByte())
            }
        }
        return this
    }

    fun getShortBE(): Short = readShort(ByteOrder.BIG_ENDIAN)

    fun putShortBE(value: Short): ByteBuffer = writeShort(value, ByteOrder.BIG_ENDIAN)

    fun getIntBE(): Int = readInt(ByteOrder.BIG_ENDIAN)

    fun putIntBE(value: Int): ByteBuffer = writeInt(value, ByteOrder.BIG_ENDIAN)

    fun getIntLE(): Int = readInt(ByteOrder.LITTLE_ENDIAN)

    fun putIntLE(value: Int): ByteBuffer = writeInt(value, ByteOrder.LITTLE_ENDIAN)

    fun getLongBE(): Long = readLong(ByteOrder.BIG_ENDIAN)

    fun putLongBE(value: Long): ByteBuffer = writeLong(value, ByteOrder.BIG_ENDIAN)

    fun getFloatBE(): Float = readFloat(ByteOrder.BIG_ENDIAN)

    fun putFloatBE(value: Float): ByteBuffer = writeFloat(value, ByteOrder.BIG_ENDIAN)

    fun getDoubleBE(): Double = readDouble(ByteOrder.BIG_ENDIAN)

    fun putDoubleBE(value: Double): ByteBuffer = writeDouble(value, ByteOrder.BIG_ENDIAN)

    // =========================================================================
    // String Conversion
    // =========================================================================

    fun toString(encoding: String = "utf8", startIndex: Int = 0, endIndex: Int = limit): String {
        val s = startIndex.coerceIn(0, limit)
        val e = endIndex.coerceIn(s, limit)
        if (s >= e) return ""
        return when (encoding.trim().lowercase()) {
            "utf8", "utf-8" -> backing.decodeToString(startIndex = s, endIndex = e)
            "ascii", "us-ascii" -> buildString(e - s) {
                for (i in s until e) {
                    append((backing[i].toInt() and 0x7F).toChar())
                }
            }
            "latin1", "iso-8859-1" -> buildString(e - s) {
                for (i in s until e) {
                    append((backing[i].toInt() and 0xFF).toChar())
                }
            }
            "utf16le", "utf-16le" -> buildString((e - s) / 2) {
                var i = s
                while (i + 1 < e) {
                    val lo = backing[i].toInt() and 0xFF
                    val hi = backing[i + 1].toInt() and 0xFF
                    append(((hi shl 8) or lo).toChar())
                    i += 2
                }
            }
            "utf16be", "utf-16be", "utf16", "utf-16" -> buildString((e - s) / 2) {
                var i = s
                while (i + 1 < e) {
                    val hi = backing[i].toInt() and 0xFF
                    val lo = backing[i + 1].toInt() and 0xFF
                    append(((hi shl 8) or lo).toChar())
                    i += 2
                }
            }
            "hex" -> {
                val hexChars = "0123456789abcdef"
                buildString((e - s) * 2) {
                    for (i in s until e) {
                        val v = backing[i].toInt() and 0xFF
                        append(hexChars[v ushr 4])
                        append(hexChars[v and 0x0F])
                    }
                }
            }
            "base64" -> encodeBase64(backing, s, e)
            else -> backing.decodeToString(startIndex = s, endIndex = e)
        }
    }

    override fun toString(): String = toString("utf8", 0, limit)

    fun readString(byteCount: Int = remaining, encoding: String = "utf8"): String {
        val count = byteCount.coerceIn(0, remaining)
        if (count == 0) return ""
        val start = position
        position += count
        return toString(encoding, start, start + count)
    }

    open fun writeString(text: String, encoding: String = "utf8"): ByteBuffer {
        val bytes = encodeString(text, encoding)
        return put(bytes, 0, bytes.size)
    }

    // =========================================================================
    // Slicing & Array Conversions
    // =========================================================================

    open fun slice(startIndex: Int = position, endIndex: Int = limit): ByteBuffer {
        val s = startIndex.coerceIn(0, limit)
        val e = endIndex.coerceIn(s, limit)
        return ByteBuffer(backing.copyOfRange(s, e), byteOrder = byteOrder)
    }

    open fun toByteArray(): ByteArray =
        if (limit == backing.size) backing.copyOf() else backing.copyOfRange(0, limit)

    open fun toByteArray(startIndex: Int, endIndex: Int = limit): ByteArray {
        val s = startIndex.coerceIn(0, limit)
        val e = endIndex.coerceIn(s, limit)
        return if (s >= e) ByteArray(0) else backing.copyOfRange(s, e)
    }

    open fun remainingByteArray(): ByteArray =
        if (position >= limit) ByteArray(0) else backing.copyOfRange(position, limit)

    internal fun internalArray(): ByteArray = backing

    open fun duplicate(): ByteBuffer =
        ByteBuffer(backing, position = position, limit = limit, byteOrder = byteOrder)

    open fun copy(): ByteBuffer =
        ByteBuffer(toByteArray(), position = position, limit = limit, byteOrder = byteOrder)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteBuffer) return false
        if (limit != other.limit) return false
        for (i in 0 until limit) {
            if (backing[i] != other.backing[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = limit
        for (i in 0 until limit) {
            result = 31 * result + backing[i]
        }
        return result
    }

    companion object {
        val EMPTY: ByteBuffer = ByteBuffer(ByteArray(0))

        fun empty(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteBuffer =
            ByteBuffer(ByteArray(0), position = 0, limit = 0, byteOrder = byteOrder)

        fun allocate(capacity: Int, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteBuffer {
            val cap = capacity.coerceAtLeast(0)
            return ByteBuffer(ByteArray(cap), position = 0, limit = cap, byteOrder = byteOrder)
        }

        fun wrap(
            bytes: ByteArray,
            offset: Int = 0,
            length: Int = bytes.size - offset,
            byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
        ): ByteBuffer {
            if (offset == 0 && length == bytes.size) {
                return ByteBuffer(bytes.copyOf(), position = 0, limit = bytes.size, byteOrder = byteOrder)
            }
            val s = offset.coerceIn(0, bytes.size)
            val e = (s + length).coerceIn(s, bytes.size)
            val slice = bytes.copyOfRange(s, e)
            return ByteBuffer(slice, position = 0, limit = slice.size, byteOrder = byteOrder)
        }

        fun fromString(
            text: String,
            encoding: String = "utf8",
            byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
        ): ByteBuffer {
            val bytes = encodeString(text, encoding)
            return ByteBuffer(bytes, position = 0, limit = bytes.size, byteOrder = byteOrder)
        }

        internal fun encodeString(text: String, encoding: String): ByteArray {
            if (text.isEmpty()) return ByteArray(0)
            return when (encoding.trim().lowercase()) {
                "utf8", "utf-8" -> text.encodeToByteArray()
                "ascii", "us-ascii" -> ByteArray(text.length) { i -> (text[i].code and 0x7F).toByte() }
                "latin1", "iso-8859-1" -> ByteArray(text.length) { i -> (text[i].code and 0xFF).toByte() }
                "utf16le", "utf-16le" -> {
                    val out = ByteArray(text.length * 2)
                    for (i in text.indices) {
                        val code = text[i].code
                        out[i * 2] = (code and 0xFF).toByte()
                        out[i * 2 + 1] = ((code ushr 8) and 0xFF).toByte()
                    }
                    out
                }
                "utf16be", "utf-16be", "utf16", "utf-16" -> {
                    val out = ByteArray(text.length * 2)
                    for (i in text.indices) {
                        val code = text[i].code
                        out[i * 2] = ((code ushr 8) and 0xFF).toByte()
                        out[i * 2 + 1] = (code and 0xFF).toByte()
                    }
                    out
                }
                "hex" -> decodeHex(text)
                "base64" -> decodeBase64(text)
                else -> text.encodeToByteArray()
            }
        }

        private fun encodeBase64(bytes: ByteArray, startIndex: Int, endIndex: Int): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val length = endIndex - startIndex
            if (length <= 0) return ""
            val sb = StringBuilder(((length + 2) / 3) * 4)
            var i = startIndex
            while (i < endIndex) {
                val b0 = bytes[i++].toInt() and 0xFF
                val hasB1 = i < endIndex
                val b1 = if (hasB1) (bytes[i++].toInt() and 0xFF) else 0
                val hasB2 = i < endIndex
                val b2 = if (hasB2) (bytes[i++].toInt() and 0xFF) else 0

                val triple = (b0 shl 16) or (b1 shl 8) or b2
                sb.append(chars[(triple ushr 18) and 0x3F])
                sb.append(chars[(triple ushr 12) and 0x3F])
                sb.append(if (hasB1) chars[(triple ushr 6) and 0x3F] else '=')
                sb.append(if (hasB2) chars[triple and 0x3F] else '=')
            }
            return sb.toString()
        }

        private fun decodeBase64(encoded: String): ByteArray {
            val clean = encoded.filter { !it.isWhitespace() }
            if (clean.isEmpty()) return ByteArray(0)
            val table = IntArray(128) { -1 }
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            for (idx in chars.indices) {
                table[chars[idx].code] = idx
            }
            table['-'.code] = 62
            table['_'.code] = 63
            val out = ArrayList<Byte>((clean.length * 3) / 4)
            var i = 0
            while (i + 1 < clean.length) {
                val c0 = clean[i].code.let { if (it < 128) table[it] else -1 }
                val c1 = clean[i + 1].code.let { if (it < 128) table[it] else -1 }
                val c2 = if (i + 2 >= clean.length || clean[i + 2] == '=') -2 else clean[i + 2].code.let { if (it < 128) table[it] else -1 }
                val c3 = if (i + 3 >= clean.length || clean[i + 3] == '=') -2 else clean[i + 3].code.let { if (it < 128) table[it] else -1 }
                if (c0 < 0 || c1 < 0) break
                val b0 = (c0 shl 2) or (c1 ushr 4)
                out.add((b0 and 0xFF).toByte())
                if (c2 >= 0) {
                    val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
                    out.add((b1 and 0xFF).toByte())
                    if (c3 >= 0) {
                        val b2 = ((c2 and 0x03) shl 6) or c3
                        out.add((b2 and 0xFF).toByte())
                    }
                }
                i += 4
            }
            return out.toByteArray()
        }

        private fun decodeHex(hex: String): ByteArray {
            val clean = hex.filter { !it.isWhitespace() }
            val len = clean.length / 2
            val out = ByteArray(len)
            for (i in 0 until len) {
                val hi = hexDigitValue(clean[i * 2])
                val lo = hexDigitValue(clean[i * 2 + 1])
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        }

        private fun hexDigitValue(ch: Char): Int = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'a'..'f' -> ch - 'a' + 10
            in 'A'..'F' -> ch - 'A' + 10
            else -> 0
        }
    }
}
