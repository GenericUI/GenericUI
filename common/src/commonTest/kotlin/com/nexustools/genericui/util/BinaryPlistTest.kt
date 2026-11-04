package com.nexustools.genericui.util

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
import kotlin.test.assertNotNull
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
}
