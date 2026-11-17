package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileSystemAvailability
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmFileSystemAndStreamsTest {

    @Test
    fun streamFileReadableAndWritableSupportFileRafStreamAndChannels(): Unit = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir", "."), "gui_streams_${System.nanoTime()}").apply {
            mkdirs()
        }
        try {
            // 1. File constructor write + append + File/Channel read
            val fileA = File(tempDir, "nested/data.bin")
            val writerA = JvmStreamFileWritableNativeImpl(fileA, append = false)
            writerA.writeText("Hello")
            writerA.flush()
            assertEquals(1, writerA.flushCount)
            writerA.endText(" World")
            writerA.close()

            val appendWriter = JvmStreamFileWritableNativeImpl(fileA, append = true)
            appendWriter.endText("!")
            appendWriter.close()

            val readerFile = JvmStreamFileReadableNativeImpl(fileA)
            assertEquals("Hello World!", readerFile.readAllText())
            readerFile.close()

            val readerChannel = JvmStreamFileReadableNativeImpl(FileInputStream(fileA).channel, fileA.absolutePath)
            assertEquals("Hello World!", readerChannel.readAllText())
            readerChannel.close()

            // 2. RandomAccessFile write + read
            val fileB = File(tempDir, "raf.bin")
            val rafWrite = RandomAccessFile(fileB, "rw")
            val rafWriter = JvmStreamFileWritableNativeImpl(rafWrite, fileB.absolutePath)
            rafWriter.write(byteArrayOf(1, 2, 3, 4))
            rafWriter.close()

            val rafRead = RandomAccessFile(fileB, "r")
            val rafReader = JvmStreamFileReadableNativeImpl(rafRead, fileB.absolutePath)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), rafReader.readAllBytes())
            rafReader.close()

            // 3. WritableByteChannel constructor
            val fileC = File(tempDir, "chan.txt")
            val chanWriter = JvmStreamFileWritableNativeImpl(FileOutputStream(fileC).channel, fileC.absolutePath)
            chanWriter.endText("channel-ok")
            chanWriter.close()
            assertEquals("channel-ok", fileC.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fileSystemBaseHandlesDiskCrudVirtualPathsAndUriHooks(): Unit = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir", "."), "gui_fs_${System.nanoTime()}").apply {
            mkdirs()
        }
        val uriStore = LinkedHashMap<String, ByteArray>()
        val events = mutableListOf<String>()
        val fs = object : JvmFileSystemBase() {
            override fun hasUriPermission(path: String, write: Boolean): FilePermissionStatus? =
                if (path.startsWith("content://")) FilePermissionStatus.GRANTED else null

            override fun readUriBytes(path: String): ByteArray? =
                if (path.startsWith("content://")) uriStore[path] else null

            override fun writeUriBytes(path: String, bytes: ByteArray, append: Boolean): Boolean? {
                if (!path.startsWith("content://")) return null
                val next = if (append) (uriStore[path] ?: ByteArray(0)) + bytes else bytes.copyOf()
                uriStore[path] = next
                return true
            }

            override fun existsUri(path: String): Boolean? =
                if (path.startsWith("content://")) uriStore.containsKey(path) else null

            override fun onDiskFileMutated(file: File, eventKind: String) {
                events.add("$eventKind:${file.name}")
            }
        }

        try {
            assertEquals(FileSystemAvailability.Always, fs.isAvailable())
            assertEquals(FilePermissionStatus.GRANTED, fs.hasPermission("content://doc/1", write = true))

            // Disk CRUD
            val subDir = File(tempDir, "work").absolutePath
            assertTrue(fs.mkdir(subDir, recursive = true))
            val docPath = File(subDir, "notes.txt").absolutePath
            assertTrue(fs.writeFile(docPath, "v1".encodeToByteArray()))
            assertTrue(fs.appendFile(docPath, "-v2".encodeToByteArray()))
            assertTrue(fs.exists(docPath))
            assertEquals("v1-v2", fs.readFile(docPath)?.decodeToString())

            val stat = fs.stat(docPath)
            assertNotNull(stat)
            assertTrue(stat.isFile)
            assertEquals(5L, stat.size)

            val copiedPath = File(subDir, "notes_copy.txt").absolutePath
            assertTrue(fs.copyFile(docPath, copiedPath, overwrite = false))
            assertFalse(fs.copyFile(docPath, copiedPath, overwrite = false))

            val renamedPath = File(subDir, "notes_final.txt").absolutePath
            assertTrue(fs.rename(copiedPath, renamedPath))
            assertFalse(fs.exists(copiedPath))
            assertEquals("v1-v2", fs.readFile(renamedPath)?.decodeToString())

            val entries = fs.readdir(subDir)
            assertTrue(entries.contains("notes.txt"))
            assertTrue(entries.contains("notes_final.txt"))

            val streamOut = fs.createWriteStream(File(subDir, "stream.txt").absolutePath, FileOpenMode.WRITE_TRUNCATE)
            streamOut.endText("streamed-fs")
            streamOut.close()
            val streamIn = fs.createReadStream(File(subDir, "stream.txt").absolutePath)
            assertNotNull(streamIn)
            assertEquals("streamed-fs", streamIn.readAllText())
            streamIn.close()

            assertTrue(fs.unlink(renamedPath))
            assertFalse(fs.exists(renamedPath))
            assertTrue(fs.rmdir(subDir, recursive = true))
            assertFalse(fs.exists(subDir))
            assertTrue(events.any { it.startsWith("write:notes.txt") })

            // URI hooks
            assertTrue(fs.writeFile("content://doc/1", "uri-body".encodeToByteArray()))
            assertTrue(fs.exists("content://doc/1"))
            assertEquals("uri-body", fs.readFile("content://doc/1")?.decodeToString())

            // Virtual path in-memory fallback
            assertTrue(fs.write("/projects/virtual/config.json", ByteBuffer.fromString("{\"ok\":true}"), append = false))
            assertEquals("{\"ok\":true}", fs.read("/projects/virtual/config.json")?.toString("utf8"))
        } finally {
            fs.dispose()
            tempDir.deleteRecursively()
        }
    }
}
