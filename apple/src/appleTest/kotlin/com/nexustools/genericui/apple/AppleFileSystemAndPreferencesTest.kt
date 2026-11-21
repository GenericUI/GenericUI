@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.util.BinaryPlist
import com.nexustools.genericui.util.UserDirectory
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleFileSystemAndPreferencesTest {

    private fun newTempDir(prefix: String): String {
        return "${NSTemporaryDirectory().trimEnd('/')}/${prefix}_${NSUUID().UUIDString}"
    }

    @Test
    fun fileSystemDiskOpsAndFileHandleStreams() {
        runBlocking {
        val fs = AppleFileSystemBase()
        val root = newTempDir("apple_fs")
        try {
            val nestedDir = fs.resolve(root, "sub", "deep")
            assertTrue(fs.mkdir(nestedDir, recursive = true))
            assertTrue(fs.exists(nestedDir))

            val filePath = fs.resolve(nestedDir, "notes.txt")
            assertEquals(FilePermissionStatus.GRANTED, fs.hasPermission(filePath, write = true))
            assertTrue(fs.writeFile(filePath, "Line1".encodeToByteArray()))
            assertTrue(fs.appendFile(filePath, "-Line2".encodeToByteArray()))
            assertEquals("Line1-Line2", fs.readFile(filePath)?.decodeToString())

            val stat = fs.stat(filePath)
            assertNotNull(stat)
            assertTrue(stat.isFile)
            assertFalse(stat.isDirectory)
            assertEquals(11L, stat.size)

            val streamPath = fs.resolve(root, "stream.bin")
            val writable = fs.createWriteStream(streamPath, FileOpenMode.WRITE_TRUNCATE)
            assertTrue(writable.write("ABCDEF".encodeToByteArray()))
            writable.end(null)

            val appender = fs.createWriteStream(streamPath, FileOpenMode.WRITE_APPEND)
            assertTrue(appender.write("GHIJ".encodeToByteArray()))
            appender.close()

            val readable = fs.createReadStream(streamPath)
            assertNotNull(readable)
            val chunk1 = readable.read(4)
            assertNotNull(chunk1)
            assertEquals("ABCD", chunk1.toByteArray().decodeToString())
            val rest = readable.readAllText()
            assertEquals("EFGHIJ", rest)
            readable.close()

            val copiedPath = fs.resolve(root, "copied.bin")
            assertTrue(fs.copyFile(streamPath, copiedPath, overwrite = true))
            assertTrue(fs.exists(copiedPath))
            val renamedPath = fs.resolve(root, "renamed.bin")
            assertTrue(fs.rename(copiedPath, renamedPath))
            assertFalse(fs.exists(copiedPath))
            assertTrue(fs.exists(renamedPath))

            val entries = fs.readdir(root)
            assertTrue(entries.contains("stream.bin"))
            assertTrue(entries.contains("renamed.bin"))

            // POSIX unlink must fail on directory; rmdir non-recursive must fail on non-empty dir.
            assertFalse(fs.unlink(root))
            assertFalse(fs.rmdir(root, recursive = false))
            assertTrue(fs.unlink(renamedPath))
            assertTrue(fs.rmdir(root, recursive = true))
            assertFalse(fs.exists(root))
        } finally {
            fs.dispose()
        }
        }
    }

    @Test
    fun userDirectoryAndBinaryPlistPreferencesPersistence() {
        runBlocking {
        val baseDir = newTempDir("apple_prefs")
        val suite = "com.nexustools.genericui.test.${NSUUID().UUIDString}"

        // Verify system directory lookup paths resolve cleanly without mutating user config.
        val appSupportDir = AppleUserDirectoryNativeImpl("UnitTestApp", "data", useCachesDirectory = false)
        assertTrue(appSupportDir.directoryPath.contains("UnitTestApp/data"))
        val cachesDir = AppleUserDirectoryNativeImpl("UnitTestApp", "caches", useCachesDirectory = true)
        assertTrue(cachesDir.directoryPath.contains("Caches"))
        val xdgDir = AppleUserDirectoryNativeImpl("UnitTestApp", "cfg", useXdgStyleConfigDirectory = true)
        assertTrue(xdgDir.directoryPath.contains(".config/UnitTestApp/cfg"))
        appSupportDir.dispose()
        cachesDir.dispose()
        xdgDir.dispose()

        val userDir1 = AppleUserDirectoryNativeImpl(
            appName = "TestApp",
            subdirectory = "prefs",
            customBaseDirectoryPath = baseDir
        )
        assertTrue(userDir1.ensureDirectoryExists())
        assertTrue(userDir1.writeBytes("sample.dat", byteArrayOf(1, 2, 3, 4)))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), userDir1.readBytes("sample.dat"))
        assertEquals(4L, userDir1.fileSize("sample.dat"))
        assertTrue(userDir1.listFiles().contains("sample.dat"))

        val specialEntries = linkedMapOf(
            "window:pos" to PreferenceElement.ofString("100,200"),
            "window/pos" to PreferenceElement.ofString("300,400"),
            "window_pos" to PreferenceElement.ofString("500,600"),
            ".hidden" to PreferenceElement.ofString("secret-dot"),
            "100%done" to PreferenceElement.ofInt(100),
            "CON" to PreferenceElement.ofString("reserved-win-name"),
            "~" to PreferenceElement.ofString("tilde-value"),
            "" to PreferenceElement.ofString("empty-key-value"),
            "café🎵" to PreferenceElement.ofString("unicode-value")
        )

        val store1 = ApplePreferenceStoreNativeImpl(
            appName = "TestApp",
            userDirectory = userDir1,
            suiteName = suite
        )
        val changes = ArrayList<String?>()
        val listener = PreferenceChangeListener { _, key, _ -> changes.add(key) }
        store1.addChangeListener(listener)
        store1.writeElement("theme", PreferenceElement.ofString("dark"))
        store1.writeElement("count", PreferenceElement.ofInt(42))
        store1.writeElement("enabled", PreferenceElement.ofBoolean(true))
        for ((key, value) in specialEntries) {
            store1.writeElement(key, value)
        }
        assertTrue(store1.flush())
        assertEquals(
            listOf<String?>("theme", "count", "enabled") + specialEntries.keys.map<String, String?> { it },
            changes
        )

        val themeBytes = userDir1.readBytes("theme.plist")
        val countBytes = userDir1.readBytes("count.plist")
        val enabledBytes = userDir1.readBytes("enabled.plist")
        assertNotNull(themeBytes)
        assertNotNull(countBytes)
        assertNotNull(enabledBytes)
        assertTrue(BinaryPlist.isBinaryPlist(themeBytes))
        assertTrue(BinaryPlist.isBinaryPlist(countBytes))
        assertTrue(BinaryPlist.isBinaryPlist(enabledBytes))
        assertNotNull(BinaryPlist.decodeElementOrNull(themeBytes))
        assertNotNull(BinaryPlist.decodeElementOrNull(countBytes))
        assertNotNull(BinaryPlist.decodeElementOrNull(enabledBytes))

        // Verify bijective special-key filenames on disk and absence of legacy key/manifest index files.
        assertEquals("window%3Apos.plist", store1.fileNameForKey("window:pos"))
        assertEquals("window%2Fpos.plist", store1.fileNameForKey("window/pos"))
        assertEquals("window_pos.plist", store1.fileNameForKey("window_pos"))
        assertEquals("%2Ehidden.plist", store1.fileNameForKey(".hidden"))
        assertEquals("100%25done.plist", store1.fileNameForKey("100%done"))
        assertEquals("%43ON.plist", store1.fileNameForKey("CON"))
        assertEquals("%7E.plist", store1.fileNameForKey("~"))
        assertEquals("~.plist", store1.fileNameForKey(""))
        assertEquals("caf%C3%A9%F0%9F%8E%B5.plist", store1.fileNameForKey("café🎵"))

        for (key in specialEntries.keys) {
            val encodedFileName = "${UserDirectory.encodePreferenceKeyToFileName(key)}.plist"
            assertFalse(encodedFileName.startsWith("."), "Encoded plist must not be a dotfile: $encodedFileName")
            assertEquals(encodedFileName, store1.fileNameForKey(key))
            val specialBytes = userDir1.readBytes(encodedFileName)
            assertNotNull(specialBytes, "Missing persisted plist for '$key' at '$encodedFileName'")
            assertTrue(BinaryPlist.isBinaryPlist(specialBytes))
            assertNotNull(BinaryPlist.decodeElementOrNull(specialBytes))
        }
        val filesAfterFlush = userDir1.listFiles()
        assertFalse(filesAfterFlush.any { it.endsWith(".old") })
        assertFalse(filesAfterFlush.contains(".preference_keys"))
        assertFalse(filesAfterFlush.contains(".user_dir_manifest"))
        assertFalse(userDir1.exists(".preference_keys"))
        assertFalse(userDir1.exists(".user_dir_manifest"))
        store1.dispose()
        userDir1.dispose()

        // Reopen with fresh UserDirectory & PreferenceStore backed by the per-key bplist00 files.
        val userDir2 = AppleUserDirectoryNativeImpl(
            appName = "TestApp",
            subdirectory = "prefs",
            customBaseDirectoryPath = baseDir
        )
        val store2 = ApplePreferenceStoreNativeImpl(
            appName = "TestApp",
            userDirectory = userDir2,
            suiteName = null
        )
        val expectedKeys = setOf("theme", "count", "enabled") + specialEntries.keys
        assertTrue(store2.contains("theme"))
        assertEquals(expectedKeys, store2.keys())
        assertEquals("dark", store2.readElement("theme")?.asString())
        assertEquals(42, store2.readElement("count")?.asInt())
        assertEquals(true, store2.readElement("enabled")?.asBoolean())
        for ((key, expectedElem) in specialEntries) {
            assertTrue(store2.contains(key), "store2 should contain '$key'")
            assertEquals(expectedElem, store2.readElement(key), "Decoded value mismatch for '$key'")
        }
        assertFalse(userDir2.listFiles().contains(".preference_keys"))
        assertFalse(userDir2.listFiles().contains(".user_dir_manifest"))
        assertFalse(userDir2.exists(".preference_keys"))
        assertFalse(userDir2.exists(".user_dir_manifest"))

        // Verify .plist.old corruption recovery (standard & encoded special-character keys)
        val validThemeBytes = userDir2.readBytes("theme.plist")!!
        assertTrue(userDir2.writeBytes("theme.plist.old", validThemeBytes))
        assertTrue(userDir2.writeBytes("theme.plist", "{CORRUPT}".encodeToByteArray()))
        assertTrue(store2.contains("theme"))
        assertEquals("dark", store2.readElement("theme")?.asString())
        assertFalse(userDir2.exists("theme.plist.old"))
        assertNotNull(BinaryPlist.decodeElementOrNull(userDir2.readBytes("theme.plist")!!))

        for (corruptKey in listOf("window:pos", ".hidden", "", "café🎵")) {
            val fileName = "${UserDirectory.encodePreferenceKeyToFileName(corruptKey)}.plist"
            val backupName = "$fileName.old"
            val validBytes = userDir2.readBytes(fileName)!!
            assertTrue(userDir2.writeBytes(backupName, validBytes))
            assertTrue(userDir2.writeBytes(fileName, "{CORRUPT}".encodeToByteArray()))
            assertTrue(store2.contains(corruptKey), "Backup recovery contains() failed for '$corruptKey'")
            assertEquals(specialEntries[corruptKey], store2.readElement(corruptKey))
            assertFalse(userDir2.exists(backupName))
            assertNotNull(BinaryPlist.decodeElementOrNull(userDir2.readBytes(fileName)!!))
        }

        // Verify orphaned .plist.old discovery without primary .plist (standard & encoded special key)
        assertTrue(userDir2.writeBytes("orphan.plist.old", BinaryPlist.encodeElement(PreferenceElement.ofString("recovered"))))
        assertTrue(store2.keys().contains("orphan"))
        assertFalse(store2.keys().any { it.endsWith(".old") })
        assertTrue(store2.contains("orphan"))
        assertEquals("recovered", store2.readElement("orphan")?.asString())
        assertFalse(userDir2.exists("orphan.plist.old"))
        assertTrue(userDir2.exists("orphan.plist"))

        val specialOrphanKey = "orphan:special/key"
        val specialOrphanFile = "${UserDirectory.encodePreferenceKeyToFileName(specialOrphanKey)}.plist"
        val specialOrphanBackup = "$specialOrphanFile.old"
        assertTrue(userDir2.writeBytes(specialOrphanBackup, BinaryPlist.encodeElement(PreferenceElement.ofString("recovered-special"))))
        assertTrue(store2.keys().contains(specialOrphanKey))
        assertTrue(store2.contains(specialOrphanKey))
        assertEquals("recovered-special", store2.readElement(specialOrphanKey)?.asString())
        assertFalse(userDir2.exists(specialOrphanBackup))
        assertTrue(userDir2.exists(specialOrphanFile))

        // Removing a special key must not clobber colliding sanitized neighbors ("window:pos" vs "window_pos", "~" vs "").
        store2.remove("window:pos")
        assertFalse(store2.contains("window:pos"))
        assertNull(store2.readElement("window:pos"))
        assertEquals("300,400", store2.readElement("window/pos")?.asString())
        assertEquals("500,600", store2.readElement("window_pos")?.asString())

        store2.remove("~")
        assertFalse(store2.contains("~"))
        assertNull(store2.readElement("~"))
        assertEquals("empty-key-value", store2.readElement("")?.asString())

        store2.remove("count")
        assertNull(store2.readElement("count"))
        store2.clear()
        assertTrue(store2.keys().isEmpty())
        assertFalse(userDir2.listFiles().any { it.endsWith(".plist") || it.endsWith(".plist.old") })
        assertFalse(userDir2.exists(".preference_keys"))
        assertFalse(userDir2.exists(".user_dir_manifest"))
        store2.dispose()
        userDir2.dispose()
        NSFileManager.defaultManager.removeItemAtPath(baseDir, error = null)
        }
    }

    @Test
    fun legacyBundleMigrationHandlesHiddenDotfilesAndSpecialKeys() {
        runBlocking {
        val baseDir = newTempDir("apple_legacy_bundle")
        val userDir = AppleUserDirectoryNativeImpl(
            appName = "LegacyApp",
            subdirectory = "prefs",
            customBaseDirectoryPath = baseDir
        )
        try {
            assertTrue(userDir.ensureDirectoryExists())
            // Presence of a hidden dotfile (e.g., .DS_Store or .hidden.plist) must not block legacy bundle migration.
            assertTrue(userDir.writeBytes(".hidden.plist", byteArrayOf(1, 2, 3)))
            val legacyBundle = PreferenceElement.ofMap(
                mapOf(
                    "window:pos" to PreferenceElement.ofString("10,20"),
                    ".hidden" to PreferenceElement.ofString("migrated-dot"),
                    "café🎵" to PreferenceElement.ofString("migrated-unicode")
                )
            )
            assertTrue(userDir.writeBytes("preferences.plist", BinaryPlist.encodeElement(legacyBundle)))

            val store = ApplePreferenceStoreNativeImpl(
                appName = "LegacyApp",
                userDirectory = userDir,
                suiteName = null
            )
            assertFalse(userDir.exists("preferences.plist"))
            assertEquals(setOf(".hidden", "café🎵", "window:pos"), store.keys())
            assertEquals("10,20", store.readElement("window:pos")?.asString())
            assertEquals("migrated-dot", store.readElement(".hidden")?.asString())
            assertEquals("migrated-unicode", store.readElement("café🎵")?.asString())
            assertTrue(userDir.exists("window%3Apos.plist"))
            assertTrue(userDir.exists("%2Ehidden.plist"))
            assertTrue(userDir.exists("caf%C3%A9%F0%9F%8E%B5.plist"))
            assertTrue(userDir.exists(".hidden.plist"))
            assertFalse(userDir.exists(".preference_keys"))
            assertFalse(userDir.exists(".user_dir_manifest"))
            store.remove(".hidden")
            assertFalse(userDir.exists("%2Ehidden.plist"))
            assertFalse(userDir.exists(".hidden.plist"))
            assertEquals(setOf("café🎵", "window:pos"), store.keys())
            store.dispose()
        } finally {
            userDir.dispose()
            NSFileManager.defaultManager.removeItemAtPath(baseDir, error = null)
        }
        }
    }

    @Test
    fun resourceResolverResolvesActiveTableAndStreams() {
        runBlocking {
        val resolver = AppleResourceResolverNativeImpl()
        try {
            val strRes = ResourceID(9001, ResourceType.STRING, "apple_greeting")
            val rawRes = ResourceID(9002, ResourceType.RAW, "apple_raw_blob")
            Resources.activeTable.registerString(strRes, "Hello Apple")
            Resources.activeTable.registerRawBytes(rawRes, "BLOB-DATA".encodeToByteArray())

            assertEquals("Hello Apple", resolver.resolveString(strRes))
            assertEquals("BLOB-DATA", resolver.resolveBytes(rawRes)?.decodeToString())

            val readable = resolver.openReadable(rawRes)
            assertNotNull(readable)
            assertEquals("BLOB-DATA", readable.readAllText())
            readable.close()
        } finally {
            resolver.dispose()
        }
        }
    }
}
