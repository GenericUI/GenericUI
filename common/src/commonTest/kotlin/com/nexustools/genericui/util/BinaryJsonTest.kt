package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.BinarySerializable
import com.nexustools.genericui.component.system.DataClassCodec
import com.nexustools.genericui.component.system.DataClassCodecRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
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

        // Unknown tags outside 0x00..0x0C (e.g. ASCII JSON '{' = 0x7B, bplist00 'b' = 0x62, 0x0D, 0xFF)
        val asciiJson = """{"theme":"dark"}""".encodeToByteArray()
        assertFalse(BinaryJson.isBinaryJson(asciiJson))
        assertNull(BinaryJson.decodeElementOrNull(asciiJson))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(0x0D)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(0xFF.toByte())))
        assertFalse(BinaryJson.isBinaryJson(BinaryPlist.encode(mapOf("a" to 1))))

        // Truncated TYPE_BYTES payloads
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BYTES)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BYTES, 0x00, 0x00, 0x00, 0x04, 0x01, 0x02)))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BYTES, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertNull(BinaryJson.decodeElementOrNull(byteArrayOf(BinaryJson.TYPE_BYTES, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x01)))

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

    private enum class CodecPriority {
        LOW,
        MEDIUM,
        HIGH
    }

    @BinarySerializable
    private data class NestedAttachment(
        val name: String,
        val payload: ByteArray,
        val priority: CodecPriority
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NestedAttachment) return false
            return name == other.name &&
                payload.contentEquals(other.payload) &&
                priority == other.priority
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + priority.hashCode()
            return result
        }
    }

    @BinarySerializable
    private data class CodecEnvelope(
        val id: String,
        val primary: NestedAttachment,
        val secondary: NestedAttachment?,
        val attachments: List<NestedAttachment>,
        val tags: Set<String>,
        val metadata: Map<String, NestedAttachment>,
        val shorts: ShortArray,
        val chars: CharArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CodecEnvelope) return false
            return id == other.id &&
                primary == other.primary &&
                secondary == other.secondary &&
                attachments == other.attachments &&
                tags == other.tags &&
                metadata == other.metadata &&
                shorts.contentEquals(other.shorts) &&
                chars.contentEquals(other.chars)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + primary.hashCode()
            result = 31 * result + (secondary?.hashCode() ?: 0)
            result = 31 * result + attachments.hashCode()
            result = 31 * result + tags.hashCode()
            result = 31 * result + metadata.hashCode()
            result = 31 * result + shorts.contentHashCode()
            result = 31 * result + chars.contentHashCode()
            return result
        }
    }

    private object NestedAttachmentCodec : DataClassCodec<NestedAttachment> {
        override fun encode(value: NestedAttachment): PreferenceElement = PreferenceElement.ofObject(
            linkedMapOf(
                "name" to PreferenceObjectCodec.toElement(value.name),
                "payload" to PreferenceObjectCodec.toElement(value.payload),
                "priority" to PreferenceObjectCodec.toElement(value.priority)
            )
        )

        override fun decode(element: PreferenceElement): NestedAttachment {
            val priorityStr = element["priority"]?.asString() ?: CodecPriority.LOW.name
            val priority = CodecPriority.entries.firstOrNull { it.name == priorityStr } ?: CodecPriority.LOW
            return NestedAttachment(
                name = element["name"]?.asString() ?: "",
                payload = element["payload"]?.asBytes() ?: ByteArray(0),
                priority = priority
            )
        }
    }

    private object CodecEnvelopeCodec : DataClassCodec<CodecEnvelope> {
        override fun encode(value: CodecEnvelope): PreferenceElement = PreferenceElement.ofObject(
            linkedMapOf(
                "id" to PreferenceObjectCodec.toElement(value.id),
                "primary" to PreferenceObjectCodec.toElement(value.primary),
                "secondary" to PreferenceObjectCodec.toElement(value.secondary),
                "attachments" to PreferenceObjectCodec.toElement(value.attachments),
                "tags" to PreferenceObjectCodec.toElement(value.tags),
                "metadata" to PreferenceObjectCodec.toElement(value.metadata),
                "shorts" to PreferenceObjectCodec.toElement(value.shorts),
                "chars" to PreferenceObjectCodec.toElement(value.chars)
            )
        )

        override fun decode(element: PreferenceElement): CodecEnvelope = CodecEnvelope(
            id = element["id"]?.asString() ?: "",
            primary = PreferenceObjectCodec.fromElement(element["primary"], NestedAttachment::class)
                ?: error("Missing primary attachment"),
            secondary = PreferenceObjectCodec.fromElement(element["secondary"], NestedAttachment::class),
            attachments = PreferenceObjectCodec.decodeList(element["attachments"]) { item ->
                PreferenceObjectCodec.fromElement(item, NestedAttachment::class)
                    ?: error("Invalid attachment item")
            } ?: emptyList(),
            tags = PreferenceObjectCodec.decodeSet(element["tags"]) { it.asString("") ?: "" } ?: emptySet(),
            metadata = PreferenceObjectCodec.decodeMap(element["metadata"]) { item ->
                PreferenceObjectCodec.fromElement(item, NestedAttachment::class)
                    ?: error("Invalid metadata attachment")
            } ?: emptyMap(),
            shorts = PreferenceObjectCodec.fromElement(element["shorts"], ShortArray::class) ?: ShortArray(0),
            chars = PreferenceObjectCodec.fromElement(element["chars"], CharArray::class) ?: CharArray(0)
        )
    }

    @Test
    fun `TYPE_BYTES encodes and decodes empty arbitrary and large ByteArrays cleanly`() = runSuspend {
        val emptyBytes = ByteArray(0)
        val encodedEmpty = BinaryJson.encode(emptyBytes)
        assertEquals(5, encodedEmpty.size)
        assertEquals(BinaryJson.TYPE_BYTES, encodedEmpty[0])
        assertTrue(BinaryJson.isBinaryJson(encodedEmpty))
        val decodedEmptyElem = BinaryJson.decodeElementOrNull(encodedEmpty)
        assertNotNull(decodedEmptyElem)
        assertEquals(PreferenceElementKind.BYTES, decodedEmptyElem.kind)
        assertTrue(decodedEmptyElem.isBytes)
        assertTrue(emptyBytes.contentEquals(decodedEmptyElem.bytesOrNull()))
        assertTrue(emptyBytes.contentEquals(BinaryJson.decode(encodedEmpty) as? ByteArray))
        assertTrue(emptyBytes.contentEquals(BinaryJson.decodeAs<ByteArray>(encodedEmpty)))

        val allByteValues = ByteArray(256) { it.toByte() }
        val elemA = PreferenceElement.fromAny(allByteValues)
        val elemB = PreferenceElement.ofBytes(allByteValues.copyOf())
        assertEquals(PreferenceElementKind.BYTES, elemA.kind)
        assertEquals(elemA, elemB)
        assertEquals(elemA.hashCode(), elemB.hashCode())
        assertTrue(allByteValues.contentEquals(elemA.toAny() as? ByteArray))

        // Mutating original input array must not mutate element
        val mutableInput = byteArrayOf(10, 20, 30)
        val immutableElem = PreferenceElement.ofBytes(mutableInput)
        mutableInput[0] = 99
        assertEquals(10.toByte(), immutableElem.bytesOrNull()?.get(0))

        val encodedAll = BinaryJson.encode(allByteValues)
        assertEquals(BinaryJson.TYPE_BYTES, encodedAll[0])
        assertEquals(1 + 4 + 256, encodedAll.size)
        val decodedAll = BinaryJson.decodeAs<ByteArray>(encodedAll)
        assertNotNull(decodedAll)
        assertTrue(allByteValues.contentEquals(decodedAll))
        assertTrue(allByteValues.contentEquals(BinaryJson.decode(encodedAll) as? ByteArray))

        // Multi-chunk > 64KB stream test
        val largeBytes = ByteArray(70_000) { ((it * 31) and 0xFF).toByte() }
        val writableBuf = WritableByteBuffer(initialCapacity = 1024, byteOrder = ByteOrder.BIG_ENDIAN)
        BinaryJson.encode(largeBytes, writableBuf)
        val readableBuf = ReadableByteBuffer(writableBuf.toByteBuffer())
        val streamDecoded = BinaryJson.decodeAs<ByteArray>(readableBuf)
        assertNotNull(streamDecoded)
        assertTrue(largeBytes.contentEquals(streamDecoded))
    }

    @Test
    fun `DataClassCodecRegistry round-trips nested data classes collections enums and primitive arrays via BinaryJson`() {
        DataClassCodecRegistry.register(NestedAttachment::class, NestedAttachmentCodec)
        DataClassCodecRegistry.register(CodecEnvelope::class, CodecEnvelopeCodec)
        try {
            assertTrue(DataClassCodecRegistry.hasCodec(NestedAttachment::class))
            assertTrue(DataClassCodecRegistry.hasCodec<CodecEnvelope>())

            val envelope = CodecEnvelope(
                id = "env-42",
                primary = NestedAttachment("icon.png", byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()), CodecPriority.HIGH),
                secondary = null,
                attachments = listOf(
                    NestedAttachment("thumb.jpg", ByteArray(0), CodecPriority.LOW),
                    NestedAttachment("shader.spv", byteArrayOf(0x03, 0x02, 0x23, 0x07), CodecPriority.MEDIUM)
                ),
                tags = linkedSetOf("ui", "binary", "kmp"),
                metadata = linkedMapOf(
                    "cover" to NestedAttachment("cover.webp", byteArrayOf(1, 2, 3, 4), CodecPriority.HIGH)
                ),
                shorts = shortArrayOf(-32768, 0, 1024, 32767),
                chars = charArrayOf('G', 'U', 'I')
            )

            val encoded = BinaryJson.encodeDataClass(envelope)
            assertEquals(BinaryJson.TYPE_OBJECT, encoded[0])
            assertTrue(BinaryJson.isBinaryJson(encoded))

            val decoded = BinaryJson.decodeDataClass<CodecEnvelope>(encoded)
            assertEquals(envelope, decoded)

            val decodedViaDecodeAs = BinaryJson.decodeAs(encoded, CodecEnvelope::class)
            assertEquals(envelope, decodedViaDecodeAs)

            // Null payload decodes to null for decodeDataClass
            assertNull(BinaryJson.decodeDataClass<CodecEnvelope>(BinaryJson.encode(null)))
            assertNull(BinaryJson.decodeDataClass<CodecEnvelope>(byteArrayOf(0x7F)))
        } finally {
            DataClassCodecRegistry.unregister<CodecEnvelope>()
            DataClassCodecRegistry.unregister<NestedAttachment>()
        }
        assertFalse(DataClassCodecRegistry.hasCodec<CodecEnvelope>())
    }
}
