package com.nexustools.genericui.util

import com.nexustools.genericui.component.system.PreferenceSerializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinaryPlistTest {

    private data class MacWindowPreference(
        val title: String,
        val width: Int,
        val height: Int,
        val retinaScale: Double,
        val maximized: Boolean,
        val recentFiles: List<String>
    ) : PreferenceSerializable {
        override fun toPreferenceMap(): Map<String, Any?> = linkedMapOf(
            "title" to title,
            "width" to width,
            "height" to height,
            "retinaScale" to retinaScale,
            "maximized" to maximized,
            "recentFiles" to recentFiles
        )
    }

    @Test
    fun `encode and decode Apple bplist00 binary property list`() {
        val pref = MacWindowPreference(
            title = "Studio Workspace — 4K",
            width = 2560,
            height = 1440,
            retinaScale = 2.0,
            maximized = true,
            recentFiles = listOf("/Users/dev/scene1.svg", "/Users/dev/scene2.svg")
        )

        val bplistBytes = BinaryPlist.encode(pref)
        assertTrue(BinaryPlist.isBinaryPlist(bplistBytes), "Encoded bytes must start with bplist00 magic header")
        assertEquals("bplist00", bplistBytes.decodeToString(0, 8))

        val decodedElement = BinaryPlist.decodeElement(bplistBytes)
        assertTrue(decodedElement.isObject)
        assertEquals("Studio Workspace — 4K", decodedElement["title"]?.asString())
        assertEquals(2560, decodedElement["width"]?.asInt())
        assertEquals(1440, decodedElement["height"]?.asInt())
        assertEquals(2.0, decodedElement["retinaScale"]?.asDouble())
        assertEquals(true, decodedElement["maximized"]?.asBoolean())
        assertEquals(
            listOf("/Users/dev/scene1.svg", "/Users/dev/scene2.svg"),
            decodedElement["recentFiles"]?.arrayElements?.map { it.asString() }
        )

        val reconstructed = BinaryPlist.decodeAs(
            bplistBytes,
            MacWindowPreference("", 0, 0, 1.0, false, emptyList())
        )
        assertEquals(pref, reconstructed)
    }
}
