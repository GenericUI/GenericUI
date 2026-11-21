package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceSerializable
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.io.ReadableByteBuffer
import com.nexustools.genericui.io.WritableByteBuffer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryJsonTest {

    private data class SampleProfile(
        val username: String,
        val level: Int,
        val premium: Boolean,
        val rating: Double,
        val tags: List<String>
    ) : PreferenceSerializable {
        override fun toPreferenceMap(): Map<String, Any?> = linkedMapOf(
            "username" to username,
            "level" to level,
            "premium" to premium,
            "rating" to rating,
            "tags" to tags
        )

        override fun fromPreferenceElements(fields: Map<String, PreferenceElement>): Any = SampleProfile(
            username = fields["username"]?.asString() ?: username,
            level = fields["level"]?.asInt(level) ?: level,
            premium = fields["premium"]?.asBoolean(premium) ?: premium,
            rating = fields["rating"]?.asDouble(rating) ?: rating,
            tags = fields["tags"]?.arrayElements?.mapNotNull { it.asString() } ?: tags
        )
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        var completed = false
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                completed = true
                failure = result.exceptionOrNull()
            }
        )
        failure?.let { throw it }
        assertTrue(completed, "Expected suspend test block to complete synchronously")
    }

    @Test
    fun `encode and decode primitives with 1-byte type tags`() {
        val nullBytes = BinaryJson.encode(null)
        assertEquals(1, nullBytes.size)
        assertEquals(BinaryJson.TYPE_NULL, nullBytes[0])
        assertNull(BinaryJson.decode(nullBytes))

        val boolTrue = BinaryJson.encode(true)
        assertEquals(2, boolTrue.size)
        assertEquals(BinaryJson.TYPE_BOOLEAN, boolTrue[0])
        assertEquals(true, BinaryJson.decode(boolTrue))

        val byteVal = BinaryJson.encode(42.toByte())
        assertEquals(2, byteVal.size)
        assertEquals(BinaryJson.TYPE_BYTE, byteVal[0])
        assertEquals(42.toByte(), BinaryJson.decode(byteVal))

        val shortVal = BinaryJson.encode(1024.toShort())
        assertEquals(3, shortVal.size)
        assertEquals(BinaryJson.TYPE_SHORT, shortVal[0])
        assertEquals(1024.toShort(), BinaryJson.decode(shortVal))

        val intVal = BinaryJson.encode(123456789)
        assertEquals(5, intVal.size)
        assertEquals(BinaryJson.TYPE_INT, intVal[0])
        assertEquals(123456789, BinaryJson.decode(intVal))

        val longVal = BinaryJson.encode(9876543210123L)
        assertEquals(9, longVal.size)
        assertEquals(BinaryJson.TYPE_LONG, longVal[0])
        assertEquals(9876543210123L, BinaryJson.decode(longVal))

        val floatVal = BinaryJson.encode(3.25f)
        assertEquals(5, floatVal.size)
        assertEquals(BinaryJson.TYPE_FLOAT, floatVal[0])
        assertEquals(3.25f, BinaryJson.decode(floatVal))

        val doubleVal = BinaryJson.encode(2.718281828)
        assertEquals(9, doubleVal.size)
        assertEquals(BinaryJson.TYPE_DOUBLE, doubleVal[0])
        assertEquals(2.718281828, BinaryJson.decode(doubleVal))

        val strVal = BinaryJson.encode("GenericUI Binary JSON")
        assertEquals(BinaryJson.TYPE_STRING, strVal[0])
        assertEquals("GenericUI Binary JSON", BinaryJson.decode(strVal))
    }

    @Test
    fun `encode and decode nested maps arrays and objects via Writable and Readable streams`() = runSuspend {
        val profile = SampleProfile(
            username = "alex",
            level = 99,
            premium = true,
            rating = 4.95,
            tags = listOf("kotlin", "vulkan", "opengl")
        )
        val encoded = BinaryJson.encode(profile)
        assertEquals(BinaryJson.TYPE_OBJECT, encoded[0])

        val elem = BinaryJson.decodeElement(encoded)
        assertTrue(elem.isObject)
        assertEquals("alex", elem["username"]?.asString())
        assertEquals(99, elem["level"]?.asInt())
        assertEquals(true, elem["premium"]?.asBoolean())
        assertEquals(4.95, elem["rating"]?.asDouble())
        assertEquals(listOf("kotlin", "vulkan", "opengl"), elem["tags"]?.arrayElements?.map { it.asString() })

        val decoded = BinaryJson.decodeAs(
            encoded,
            SampleProfile(username = "", level = 0, premium = false, rating = 0.0, tags = emptyList())
        )
        assertEquals(profile, decoded)

        // Direct WritableByteBuffer & ReadableByteBuffer (via Writable / Readable interfaces)
        val writableBuf = WritableByteBuffer(initialCapacity = 64, byteOrder = ByteOrder.LITTLE_ENDIAN)
        BinaryJson.encode(profile, writableBuf)
        val readableBuf = ReadableByteBuffer(writableBuf.toByteBuffer())
        val streamDecoded = BinaryJson.decodeAs(
            readableBuf,
            SampleProfile(username = "", level = 0, premium = false, rating = 0.0, tags = emptyList())
        )
        assertEquals(profile, streamDecoded)

        // Direct FileSystem Writable & Readable stream roundtrip
        FileSystem.resetFallback()
        val fileOut = FileSystem.openWritable("/tmp/profile.bson", append = false)
        BinaryJson.encode(profile, fileOut)
        fileOut.end()
        val fileIn = FileSystem.openReadable("/tmp/profile.bson")
        assertNotNull(fileIn)
        val fileDecoded = BinaryJson.decodeAs(
            fileIn,
            SampleProfile(username = "", level = 0, premium = false, rating = 0.0, tags = emptyList())
        )
        assertEquals(profile, fileDecoded)
    }

    @Test
    fun `isBinaryJson and decodeElementOrNull strictly reject empty unknown truncated out-of-bounds and trailing bytes`() {
        // Valid Null (0x00) vs empty/corrupt (Kotlin null)
        val validNullBytes = BinaryJson.encodeElement(PreferenceElement.Null)
        assertTrue(BinaryJson.isBinaryJson(validNullBytes))
        val decodedNull = BinaryJson.decodeElementOrNull(validNullBytes)
        assertNotNull(decodedNull)
        assertTrue(decodedNull.isNull)

        assertFalse(BinaryJson.isBinaryJson(byteArrayOf()))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf()))
        assertNull(BinaryJson.decodeElementOrNull(ByteBuffer.empty()))

        val exhaustedBuf = ByteBuffer.wrap(validNullBytes).apply { position = limit }
        assertNull(BinaryJson.decodeElementOrNull(exhaustedBuf))
        assertFalse(BinaryJson.isBinaryJson(exhaustedBuf))

        // Unknown tags outside 0x00..0x0B (e.g. ASCII JSON '{' = 0x7B, bplist00 'b' = 0x62, 0x0C, 0xFF)
        val asciiJson = """{"theme":"dark"}""".encodeToByteArray()
        assertFalse(BinaryJson.isBinaryJson(asciiJson))
        assertNull(BinaryJson.decodeElementOrNull(asciiJson))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(0x0C)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(0xFF.toByte())))
        assertFalse(BinaryJson.isBinaryJson(BinaryPlist.encode(mapOf("a" to 1))))

        // Truncated primitive payloads
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BOOLEAN)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BYTE)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_SHORT, 0x01)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_INT, 0x00, 0x01)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_LONG, 0x00, 0x01, 0x02, 0x03)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_FLOAT, 0x3F, 0x80.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_DOUBLE, 0x40, 0x00, 0x00)))

        // Truncated / negative / out-of-bounds string, array, map, and object lengths (must not OOM)
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_STRING, 0x00, 0x00)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_STRING, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_STRING, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x41)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_STRING, 0x00, 0x00, 0x00, 0x05, 0x41, 0x42)))

        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_ARRAY, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_ARRAY, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_ARRAY, 0x00, 0x00, 0x00, 0x02, BinaryJson.TYPE_NULL)))

        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_MAP, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_OBJECT, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_OBJECT, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x61)))

        // Verify non-strict decodeElement also handles malicious huge counts without OOM
        val maliciousHugeArray = byteArrayOf(BinaryJson.TYPE_ARRAY, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertTrue(BinaryJson.decodeElement(maliciousHugeArray).isArray)
        val maliciousHugeString = byteArrayOf(BinaryJson.TYPE_STRING, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x41)
        assertEquals("", BinaryJson.decodeElement(maliciousHugeString).asString())

        // Trailing unconsumed bytes must fail strict decoding
        val validIntBytes = BinaryJson.encode(12345)
        val withTrailing = validIntBytes + byteArrayOf(BinaryJson.TYPE_NULL)
        assertFalse(BinaryJson.isBinaryJson(withTrailing))
        assertNull(BinaryJson.decodeElementOrNull(withTrailing))

        val bufWithTrailing = ByteBuffer.wrap(withTrailing)
        assertNull(BinaryJson.decodeElementOrNull(bufWithTrailing))
        assertEquals(0, bufWithTrailing.position)

        // Sliced ByteBuffer starting at offset > 0 succeeds and advances position to limit
        val framedBytes = byteArrayOf(0x7F, 0x7F) + validIntBytes
        val framedBuf = ByteBuffer.wrap(framedBytes).apply { position = 2 }
        assertTrue(BinaryJson.isBinaryJson(framedBuf))
        assertEquals(2, framedBuf.position)
        val slicedDecoded = BinaryJson.decodeElementOrNull(framedBuf)
        assertNotNull(slicedDecoded)
        assertEquals(12345, slicedDecoded.asInt())
        assertEquals(framedBuf.limit, framedBuf.position)
    }
}
