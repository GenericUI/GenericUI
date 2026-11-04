package com.nexustools.genericui.io

/**
 * NodeJS-like asynchronous coroutine stream pipeline and conversion utilities for [Readable] and [Writable].
 */
object StreamUtil {

    /**
     * Asynchronously pipes all chunks from [source] to [destination], closing both streams when
     * [closeBoth] is `true`, and returns the total number of bytes transferred.
     */
    suspend fun pipeline(
        source: Readable,
        destination: Writable,
        closeBoth: Boolean = true
    ): Long {
        return try {
            source.pipe(destination, endDestination = true)
        } finally {
            if (closeBoth) {
                try {
                    source.close()
                } finally {
                    destination.close()
                }
            }
        }
    }

    /**
     * Asynchronously copies chunks from [source] to [destination] without closing either stream.
     */
    suspend fun copy(
        source: Readable,
        destination: Writable,
        chunkSize: Int = 65536
    ): Long = source.pipe(destination, endDestination = false, chunkSize = chunkSize)

    /**
     * Asynchronously drains [source] into a [ByteArray] and closes [source].
     */
    suspend fun toByteArray(source: Readable): ByteArray =
        try {
            source.readAllBytes()
        } finally {
            source.close()
        }

    /**
     * Asynchronously drains [source] into a [ByteBuffer] and closes [source].
     */
    suspend fun toByteBuffer(source: Readable): ByteBuffer =
        try {
            source.readAllBuffer()
        } finally {
            source.close()
        }

    /**
     * Asynchronously drains [source] into a UTF-8 [String] and closes [source].
     */
    suspend fun toText(source: Readable, encoding: String = "utf8"): String =
        try {
            source.readAllText(encoding)
        } finally {
            source.close()
        }

    /**
     * Asynchronously transforms each chunk from [source] with [transformer] and writes the transformed
     * bytes to [destination], ending [destination] when [source] reaches EOF.
     */
    suspend fun transform(
        source: Readable,
        destination: Writable,
        transformer: suspend (ByteArray) -> ByteArray
    ): Long {
        var totalWritten = 0L
        try {
            source.forEachChunk { chunk ->
                val transformed = transformer(chunk.remainingByteArray())
                if (transformed.isNotEmpty()) {
                    destination.write(transformed)
                    totalWritten += transformed.size.toLong()
                }
            }
            destination.end()
        } finally {
            source.close()
        }
        return totalWritten
    }

    fun fromByteArray(bytes: ByteArray): Readable =
        Readable.fromBytes(bytes)

    fun fromByteBuffer(buffer: ByteBuffer): Readable =
        Readable.fromBuffer(buffer)

    fun fromString(text: String): Readable =
        Readable.fromText(text)
}
