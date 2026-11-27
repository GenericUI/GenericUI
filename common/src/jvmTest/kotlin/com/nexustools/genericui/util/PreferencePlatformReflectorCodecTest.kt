package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.component.system.PreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencePlatformReflectorCodecTest {

    private enum class ReflectMode {
        LOW,
        MEDIUM,
        HIGH
    }

    private data class NestedItem(
        val key: String,
        val weight: Int = 10,
        val blob: ByteArray = byteArrayOf(1, 2, 3)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NestedItem) return false
            return key == other.key && weight == other.weight && blob.contentEquals(other.blob)
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + weight
            result = 31 * result + blob.contentHashCode()
            return result
        }
    }

    private data class ComplexReflectedModel(
        val id: String,
        val retries: Int = 7,
        val enabled: Boolean = true,
        val mode: ReflectMode = ReflectMode.HIGH,
        val label: String? = "default-label",
        val nestedDefault: NestedItem = NestedItem("fallback", 99, byteArrayOf(9, 8)),
        val nestedMaps: List<Map<String, NestedItem>>,
        val groupedLists: Map<String, List<NestedItem>>,
        val modeSet: Set<ReflectMode>,
        val itemSet: Set<NestedItem>,
        val bytes: ByteArray,
        val shorts: ShortArray,
        val chars: CharArray,
        val ints: IntArray,
        val longs: LongArray,
        val floats: FloatArray,
        val doubles: DoubleArray,
        val booleans: BooleanArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ComplexReflectedModel) return false
            return id == other.id &&
                retries == other.retries &&
                enabled == other.enabled &&
                mode == other.mode &&
                label == other.label &&
                nestedDefault == other.nestedDefault &&
                nestedMaps == other.nestedMaps &&
                groupedLists == other.groupedLists &&
                modeSet == other.modeSet &&
                itemSet == other.itemSet &&
                bytes.contentEquals(other.bytes) &&
                shorts.contentEquals(other.shorts) &&
                chars.contentEquals(other.chars) &&
                ints.contentEquals(other.ints) &&
                longs.contentEquals(other.longs) &&
                floats.contentEquals(other.floats) &&
                doubles.contentEquals(other.doubles) &&
                booleans.contentEquals(other.booleans)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + retries
            result = 31 * result + enabled.hashCode()
            result = 31 * result + mode.hashCode()
            result = 31 * result + (label?.hashCode() ?: 0)
            result = 31 * result + nestedDefault.hashCode()
            result = 31 * result + nestedMaps.hashCode()
            result = 31 * result + groupedLists.hashCode()
            result = 31 * result + modeSet.hashCode()
            result = 31 * result + itemSet.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + shorts.contentHashCode()
            result = 31 * result + chars.contentHashCode()
            result = 31 * result + ints.contentHashCode()
            result = 31 * result + longs.contentHashCode()
            result = 31 * result + floats.contentHashCode()
            result = 31 * result + doubles.contentHashCode()
            result = 31 * result + booleans.contentHashCode()
            return result
        }
    }

    private data class PartialDefaultsRecord(
        val id: String,
        val retries: Int = 7,
        val enabled: Boolean = true,
        val mode: ReflectMode = ReflectMode.HIGH,
        val label: String? = "default-label",
        val nested: NestedItem = NestedItem("default-nested", 42, byteArrayOf(7, 6))
    )

    private data class AllDefaultsWithInit(
        val host: String = "localhost",
        val port: Int = 8443,
        val secure: Boolean = true
    ) {
        val endpointSummary: String = "$host:$port:$secure"
    }

    private data class LargeDefaultsRecord(
        val f00: Int = 100,
        val f01: String = "one",
        val f02: Int = 2,
        val f03: Int = 3,
        val f04: Int = 4,
        val f05: Int = 5,
        val f06: Int = 6,
        val f07: Int = 7,
        val f08: Int = 8,
        val f09: Int = 9,
        val f10: Int = 10,
        val f11: Int = 11,
        val f12: Int = 12,
        val f13: Int = 13,
        val f14: Int = 14,
        val f15: Int = 15,
        val f16: Int = 16,
        val f17: Int = 17,
        val f18: Int = 18,
        val f19: Int = 19,
        val f20: Int = 20,
        val f21: Int = 21,
        val f22: Int = 22,
        val f23: Int = 23,
        val f24: Int = 24,
        val f25: Int = 25,
        val f26: Int = 26,
        val f27: Int = 27,
        val f28: Int = 28,
        val f29: Int = 29,
        val f30: Int = 30,
        val f31: Int = 31,
        val f32: Int = 320,
        val f33: String = "thirty-three",
        val f34: Boolean = true
    )

    @Test
    fun `nested data classes wildcard generics and primitive arrays round-trip via BinaryJson BinaryPlist and PreferenceStore`() {
        val itemA = NestedItem("alpha", 1, byteArrayOf(0x00, 0x7F, 0xFF.toByte()))
        val itemB = NestedItem("beta", 2, byteArrayOf(0x10, 0x20))
        val itemC = NestedItem("gamma", 3, ByteArray(0))

        val model = ComplexReflectedModel(
            id = "render-pipeline-1",
            retries = 3,
            enabled = false,
            mode = ReflectMode.MEDIUM,
            label = null,
            nestedDefault = itemA,
            nestedMaps = listOf(
                linkedMapOf("a" to itemA, "b" to itemB),
                linkedMapOf("c" to itemC)
            ),
            groupedLists = linkedMapOf(
                "primary" to listOf(itemA, itemC),
                "secondary" to listOf(itemB)
            ),
            modeSet = linkedSetOf(ReflectMode.LOW, ReflectMode.HIGH),
            itemSet = linkedSetOf(itemA, itemB),
            bytes = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte()),
            shorts = shortArrayOf(-32768, -1, 0, 1024, 32767),
            chars = charArrayOf('K', 'M', 'P'),
            ints = intArrayOf(-100, 0, 42, Int.MAX_VALUE),
            longs = longArrayOf(-1L, 0L, 9876543210123L),
            floats = floatArrayOf(-1.5f, 0f, 3.25f),
            doubles = doubleArrayOf(2.718281828, 3.141592653),
            booleans = booleanArrayOf(true, false, true)
        )

        // BinaryJson reflection round-trip
        val bsonBytes = BinaryJson.encodeDataClass(model)
        val bsonDecoded = BinaryJson.decodeDataClass<ComplexReflectedModel>(bsonBytes)
        assertEquals(model, bsonDecoded)
        assertEquals(model, BinaryJson.decodeAs<ComplexReflectedModel>(bsonBytes))

        // BinaryPlist reflection round-trip
        val plistBytes = BinaryPlist.encodeDataClass(model)
        assertTrue(BinaryPlist.isBinaryPlist(plistBytes))
        val plistDecoded = BinaryPlist.decodeDataClass<ComplexReflectedModel>(plistBytes)
        assertEquals(model, plistDecoded)
        assertEquals(model, BinaryPlist.decodeAs<ComplexReflectedModel>(plistBytes))

        // PreferenceStore reflection round-trip
        val store = PreferenceStore.open("ReflectorTestApp")
        try {
            store.put("complex_model", model)
            val fromStore = store.get<ComplexReflectedModel>("complex_model")
            assertEquals(model, fromStore)
        } finally {
            store.clear()
            store.dispose()
        }
    }

    @Test
    fun `omitted constructor parameters invoke Kotlin default bridge and preserve defaults and explicit nulls`() {
        val partialElement = PreferenceElement.ofObject(
            linkedMapOf(
                "id" to PreferenceElement.ofString("job-99")
            )
        )
        val partialEncoded = BinaryJson.encodeElement(partialElement)
        val decodedPartial = BinaryJson.decodeDataClass<PartialDefaultsRecord>(partialEncoded)
        assertNotNull(decodedPartial)
        assertEquals("job-99", decodedPartial.id)
        assertEquals(7, decodedPartial.retries)
        assertTrue(decodedPartial.enabled)
        assertEquals(ReflectMode.HIGH, decodedPartial.mode)
        assertEquals("default-label", decodedPartial.label)
        assertEquals(NestedItem("default-nested", 42, byteArrayOf(7, 6)), decodedPartial.nested)

        // Explicit null for nullable parameter with non-null default must stay null instead of reverting
        val explicitNullElement = PreferenceElement.ofObject(
            linkedMapOf(
                "id" to PreferenceElement.ofString("job-100"),
                "label" to PreferenceElement.Null,
                "retries" to PreferenceElement.Null
            )
        )
        val explicitNullDecoded = PreferenceObjectCodec.fromElement(
            explicitNullElement,
            PartialDefaultsRecord::class
        )
        assertNotNull(explicitNullDecoded)
        assertEquals("job-100", explicitNullDecoded.id)
        assertNull(explicitNullDecoded.label)
        assertEquals(7, explicitNullDecoded.retries)
        assertEquals(ReflectMode.HIGH, explicitNullDecoded.mode)

        // All-defaults data class uses $default constructor first so init-time derived properties see decoded values
        val hostOnlyPlist = BinaryPlist.encodeElement(
            PreferenceElement.ofObject(
                linkedMapOf("host" to PreferenceElement.ofString("api.nexustools.dev"))
            )
        )
        val decodedHostOnly = BinaryPlist.decodeDataClass<AllDefaultsWithInit>(hostOnlyPlist)
        assertNotNull(decodedHostOnly)
        assertEquals("api.nexustools.dev", decodedHostOnly.host)
        assertEquals(8443, decodedHostOnly.port)
        assertTrue(decodedHostOnly.secure)
        assertEquals("api.nexustools.dev:8443:true", decodedHostOnly.endpointSummary)

        // > 32 parameters (2 bitmask words)
        val largePartial = PreferenceElement.ofObject(
            linkedMapOf(
                "f01" to PreferenceElement.ofString("custom-one"),
                "f32" to PreferenceElement.ofInt(777)
            )
        )
        val largeDecoded = BinaryJson.decodeDataClass<LargeDefaultsRecord>(BinaryJson.encodeElement(largePartial))
        assertNotNull(largeDecoded)
        assertEquals(100, largeDecoded.f00)
        assertEquals("custom-one", largeDecoded.f01)
        assertEquals(31, largeDecoded.f31)
        assertEquals(777, largeDecoded.f32)
        assertEquals("thirty-three", largeDecoded.f33)
        assertTrue(largeDecoded.f34)
    }
}
