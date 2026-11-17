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

        val store1 = ApplePreferenceStoreNativeImpl(
            appName = "TestApp",
            userDirectory = userDir1,
            fileName = "settings.plist",
            suiteName = suite
        )
        val changes = ArrayList<String?>()
        val listener = PreferenceChangeListener { _, key, _ -> changes.add(key) }
        store1.addChangeListener(listener)
        store1.writeElement("theme", PreferenceElement.ofString("dark"))
        store1.writeElement("count", PreferenceElement.ofInt(42))
        store1.writeElement("enabled", PreferenceElement.ofBoolean(true))
        assertTrue(store1.flush())
        assertEquals(listOf<String?>("theme", "count", "enabled"), changes)

        val rawPlistBytes = userDir1.readBytes("settings.plist")
        assertNotNull(rawPlistBytes)
        assertTrue(BinaryPlist.isBinaryPlist(rawPlistBytes))
        store1.dispose()
        userDir1.dispose()

        // Reopen with fresh UserDirectory & PreferenceStore backed by the persisted bplist00 file.
        val userDir2 = AppleUserDirectoryNativeImpl(
            appName = "TestApp",
            subdirectory = "prefs",
            customBaseDirectoryPath = baseDir
        )
        val store2 = ApplePreferenceStoreNativeImpl(
            appName = "TestApp",
            userDirectory = userDir2,
            fileName = "settings.plist",
            suiteName = null
        )
        assertTrue(store2.contains("theme"))
        assertEquals("dark", store2.readElement("theme")?.asString())
        assertEquals(42, store2.readElement("count")?.asInt())
        assertEquals(true, store2.readElement("enabled")?.asBoolean())

        store2.remove("count")
        assertNull(store2.readElement("count"))
        store2.clear()
        assertTrue(store2.keys().isEmpty())
        store2.dispose()
        userDir2.dispose()
        NSFileManager.defaultManager.removeItemAtPath(baseDir, error = null)
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
