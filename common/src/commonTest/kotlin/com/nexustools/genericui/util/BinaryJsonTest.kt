package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceSerializable
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `encode and decode nested maps arrays and objects`() {
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
    }
}
