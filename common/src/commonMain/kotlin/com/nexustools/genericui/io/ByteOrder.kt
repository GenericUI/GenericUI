package com.nexustools.genericui.io

/**
 * Byte endianness order for multi-byte primitive read/write operations on [ByteBuffer],
 * [WritableByteBuffer], [ReadableByteBuffer], [Readable], [Writable], and [Stream].
 *
 * Defaults to [LITTLE_ENDIAN] (the most common native CPU and binary protocol endianness).
 */
enum class ByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN
}
