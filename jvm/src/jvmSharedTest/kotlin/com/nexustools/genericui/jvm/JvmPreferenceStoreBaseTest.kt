package com.nexustools.genericui.jvm

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.util.BinaryJson
import com.nexustools.genericui.util.BinaryPlist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmPreferenceStoreBaseTest {

    private data class PlaybackSettings(
        val deviceName: String,
        val sampleRate: Int,
        val gain: Double,
        val normalized: Boolean,
        val tags: List<String>
    )

    @Test
    fun writesPerKeyBsonAndPlistFilesAndNotifiesListeners() {
        val tempDir = File(System.getProperty("java.io.tmpdir", "."), "gui_prefs_${System.nanoTime()}").apply {
            mkdirs()
        }
        try {
            for (forcePlist in listOf(false, true)) {
                val subDir = File(tempDir, if (forcePlist) "plist" else "bson").apply { mkdirs() }
                val events = mutableListOf<String>()
                val store = object : JvmPreferenceStoreBase(appName = "TestPrefsApp", customDirectory = subDir) {
                    override val useBinaryPlist: Boolean = forcePlist
                }
                val listener = PreferenceChangeListener { _, key, value ->
                    events.add("${key ?: "*"}:${value?.toString() ?: "null"}")
                }
                store.addChangeListener(listener)

                val sample = PlaybackSettings(
                    deviceName = "DAC Output",
                    sampleRate = 96000,
                    gain = 0.75,
                    normalized = true,
                    tags = listOf("hifi", "lossless")
                )
                store.writeElement("playback", PreferenceObjectCodec.toElement(sample))
                store.writeElement("theme", PreferenceElement.ofString("dark"))
                assertTrue(store.flush())

                val keyFile = store.fileForKey("playback")
                assertTrue(keyFile.exists())
                if (forcePlist) {
                    assertTrue(keyFile.name.endsWith(".plist"))
                    assertTrue(BinaryPlist.isBinaryPlist(keyFile.readBytes()))
                } else {
                    assertTrue(keyFile.name.endsWith(".bson"))
                    assertEquals(BinaryJson.TYPE_OBJECT, keyFile.readBytes()[0])
                }

                // Reopen fresh instance against the same directory
                val reopened = object : JvmPreferenceStoreBase(appName = "TestPrefsApp", customDirectory = subDir) {
                    override val useBinaryPlist: Boolean = forcePlist
                }
                assertTrue(reopened.contains("playback"))
                assertEquals(setOf("playback", "theme"), reopened.keys())
                val restored = PreferenceObjectCodec.fromElement(
                    reopened.readElement("playback"),
                    PlaybackSettings::class
                )
                assertNotNull(restored)
                assertEquals(sample, restored)
                assertEquals("dark", reopened.readElement("theme")?.asString())

                store.remove("theme")
                assertFalse(store.contains("theme"))
                assertTrue(events.any { it.startsWith("playback:") })
                assertTrue(events.any { it == "theme:null" })

                store.clear()
                assertTrue(store.keys().isEmpty())
                assertTrue(events.last() == "*:null")

                reopened.dispose()
                store.dispose()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
