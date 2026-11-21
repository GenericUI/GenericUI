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

        override fun fromPreferenceElements(fields: Map<String, PreferenceElement>): Any = MacWindowPreference(
            title = fields["title"]?.asString() ?: title,
            width = fields["width"]?.asInt(width) ?: width,
            height = fields["height"]?.asInt(height) ?: height,
            retinaScale = fields["retinaScale"]?.asDouble(retinaScale) ?: retinaScale,
            maximized = fields["maximized"]?.asBoolean(maximized) ?: maximized,
            recentFiles = fields["recentFiles"]?.arrayElements?.mapNotNull { it.asString() } ?: recentFiles
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
    fun `encode and decode Apple bplist00 binary property list via Writable and Readable streams`() = runSuspend {
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
        assertEquals("bplist00", ByteBuffer.wrap(bplistBytes).toString("utf8", 0, 8))

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

        // Direct WritableByteBuffer & ReadableByteBuffer (via Writable / Readable interfaces)
        val writableBuf = WritableByteBuffer(initialCapacity = 128, byteOrder = ByteOrder.BIG_ENDIAN)
        BinaryPlist.encode(pref, writableBuf)
        val readableBuf = ReadableByteBuffer(writableBuf.toByteBuffer())
        val streamReconstructed = BinaryPlist.decodeAs(
            readableBuf,
            MacWindowPreference("", 0, 0, 1.0, false, emptyList())
        )
        assertEquals(pref, streamReconstructed)

        // Direct FileSystem Writable & Readable stream roundtrip
        FileSystem.resetFallback()
        val fileOut = FileSystem.openWritable("/tmp/window.plist", append = false)
        BinaryPlist.encode(pref, fileOut)
        fileOut.end()
        val fileIn = FileSystem.openReadable("/tmp/window.plist")
        assertNotNull(fileIn)
        val fileReconstructed = BinaryPlist.decodeAs(
            fileIn,
            MacWindowPreference("", 0, 0, 1.0, false, emptyList())
        )
        assertEquals(pref, fileReconstructed)
    }

    @Test
    fun `decodeElementOrNull distinguishes valid bplist00 null from corrupt truncated out-of-bounds and cyclic bplist00`() {
        // Valid bplist00 encoding 0x00 (Null) must decode to PreferenceElement.Null (non-null)
        val validNullPlist = BinaryPlist.encodeElement(PreferenceElement.Null)
        assertTrue(BinaryPlist.isBinaryPlist(validNullPlist))
        val decodedNull = BinaryPlist.decodeElementOrNull(validNullPlist)
        assertNotNull(decodedNull)
        assertTrue(decodedNull.isNull)

        // Empty / wrong header / too short
        assertNull(BinaryPlist.decodeElementOrNull(byteArrayOf()))
        assertNull(BinaryPlist.decodeElementOrNull(ByteBuffer.empty()))
        assertNull(BinaryPlist.decodeElementOrNull("{CORRUPT}".encodeToByteArray()))
        assertNull(BinaryPlist.decodeElementOrNull("bplist00short".encodeToByteArray()))
        assertNull(BinaryPlist.decodeElementOrNull(ByteArray(40) { 0x41 }))

        val validPayload = BinaryPlist.encode(mapOf("theme" to "dark", "count" to 42))
        assertNotNull(BinaryPlist.decodeElementOrNull(validPayload))
        assertNotNull(BinaryPlist.decodeElementOrNull(ByteBuffer.wrap(validPayload)))

        // Truncated by 1 byte
        assertNull(BinaryPlist.decodeElementOrNull(validPayload.copyOf(validPayload.size - 1)))

        // Invalid trailer offsetIntSize (3) and objectRefSize (5)
        val badOffsetSize = validPayload.copyOf()
        badOffsetSize[badOffsetSize.size - 32 + 6] = 3
        assertNull(BinaryPlist.decodeElementOrNull(badOffsetSize))

        val badRefSize = validPayload.copyOf()
        badRefSize[badRefSize.size - 32 + 7] = 5
        assertNull(BinaryPlist.decodeElementOrNull(badRefSize))

        // Out-of-bounds numObjects in trailer (must return null without OOM)
        val badNumObjects = validPayload.copyOf()
        val numObjPos = badNumObjects.size - 32 + 8
        badNumObjects[numObjPos + 4] = 0x7F
        badNumObjects[numObjPos + 5] = 0xFF.toByte()
        badNumObjects[numObjPos + 6] = 0xFF.toByte()
        badNumObjects[numObjPos + 7] = 0xFF.toByte()
        assertNull(BinaryPlist.decodeElementOrNull(badNumObjects))

        // Invalid topObjectIndex >= numObjects
        val badTopIdx = validPayload.copyOf()
        badTopIdx[badTopIdx.size - 32 + 23] = 99
        assertNull(BinaryPlist.decodeElementOrNull(badTopIdx))

        // Object offset pointing outside [8, offsetTableStart)
        val badOffsetLow = validNullPlist.copyOf()
        val trailerStart = badOffsetLow.size - 32
        val offsetTableStart = badOffsetLow[trailerStart + 31].toInt() and 0xFF
        badOffsetLow[offsetTableStart] = 4 // < 8
        assertNull(BinaryPlist.decodeElementOrNull(badOffsetLow))

        val badOffsetHigh = validNullPlist.copyOf()
        badOffsetHigh[offsetTableStart] = offsetTableStart.toByte() // >= offsetTableStart
        assertNull(BinaryPlist.decodeElementOrNull(badOffsetHigh))

        // Hand-crafted minimal cyclic bplist00 (object 0 is Array[ref 0])
        val cyclicBplist = byteArrayOf(
            // Header "bplist00" (8 bytes, indices 0..7)
            0x62, 0x70, 0x6C, 0x69, 0x73, 0x74, 0x30, 0x30,
            // Object 0 at offset 8: Array of 1 element -> ref 0 (2 bytes, indices 8..9)
            0xA1.toByte(), 0x00,
            // Offset table at index 10: [8] (1 byte)
            0x08,
            // 32-byte trailer at index 11..42
            0, 0, 0, 0, 0, 0,
            1, // offsetIntSize
            1, // objectRefSize
            0, 0, 0, 0, 0, 0, 0, 1, // numObjects = 1
            0, 0, 0, 0, 0, 0, 0, 0, // topObjectIndex = 0
            0, 0, 0, 0, 0, 0, 0, 10 // offsetTableStart = 10
        )
        assertNull(BinaryPlist.decodeElementOrNull(cyclicBplist))

        // Truncated ASCII string (declares length 10 inside 2-byte object body)
        val truncatedStringBplist = byteArrayOf(
            0x62, 0x70, 0x6C, 0x69, 0x73, 0x74, 0x30, 0x30,
            0x5A, 0x41, 0x42, // ASCII string length 10, only 2 bytes present before offset table
            0x08,
            0, 0, 0, 0, 0, 0, 1, 1,
            0, 0, 0, 0, 0, 0, 0, 1,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 11
        )
        assertNull(BinaryPlist.decodeElementOrNull(truncatedStringBplist))

        // Unknown marker (0x70) must return null (unlike 0x00 Null)
        val unknownMarkerBplist = validNullPlist.copyOf()
        unknownMarkerBplist[8] = 0x70
        assertFalse(unknownMarkerBplist.contentEquals(validNullPlist))
        assertNull(BinaryPlist.decodeElementOrNull(unknownMarkerBplist))
    }
}
