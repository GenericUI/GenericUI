package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmUiThreadDispatcherAndDatabaseResourceTest {

    @Test
    fun uiThreadDispatcherSeparatesLogicAndUiThreadsAndSupportsBlockingRoundTrip() {
        var fakeMainThread: Thread? = Thread.currentThread()
        val postedToUi = ArrayList<() -> Unit>()
        val dispatcher = object : JvmUiThreadDispatcherBase("GenericUI-Test-Logic") {
            override fun isOnToolkitMainThread(): Boolean = Thread.currentThread() === fakeMainThread
            override fun postToToolkitUiThread(task: () -> Unit) {
                postedToUi.add(task)
            }
        }

        assertTrue(dispatcher.isSplitThreadingEnabled)
        assertTrue(dispatcher.isOnUiThread())
        assertFalse(dispatcher.isOnLogicThread())

        val trace = ArrayList<String>()
        dispatcher.dispatchToLogic {
            assertTrue(dispatcher.isOnLogicThread())
            assertFalse(dispatcher.isOnUiThread())
            trace.add("logic-start")

            val syncValue = dispatcher.runOnUiBlocking {
                assertTrue(dispatcher.isOnUiThread())
                assertFalse(dispatcher.isOnLogicThread())
                "from-ui-sync"
            }
            trace.add("sync:$syncValue")

            dispatcher.dispatchToUi {
                assertTrue(dispatcher.isOnUiThread())
                assertFalse(dispatcher.isOnLogicThread())
                trace.add("ui-queued")
            }
            assertTrue(dispatcher.isOnLogicThread())
            trace.add("logic-end")
        }

        assertEquals(listOf("logic-start", "sync:from-ui-sync", "logic-end", "ui-queued"), trace)

        // When invoked asynchronously off the toolkit UI thread, posts wakeup via postToToolkitUiThread
        // instead of executing UI mutations directly on the caller's background thread.
        fakeMainThread = null
        dispatcher.dispatchToUi {
            trace.add("async-ui")
        }
        assertFalse(trace.contains("async-ui"))
        assertEquals(1, postedToUi.size)
        fakeMainThread = Thread.currentThread()
        postedToUi.removeAt(0).invoke()
        assertTrue(trace.contains("async-ui"))
    }

    @Test
    fun databaseBaseAndResourceResolverBaseHandleQueriesBlobsAndLocalFiles(): Unit = runBlocking {
        val db = object : JvmDatabaseBase(databaseName = "JvmBaseDb", filePath = ":memory:") {}
        val schema = DatabaseTableSchema(
            tableName = "tracks",
            primaryKeyColumn = "key",
            columns = listOf(
                DatabaseColumnSpec("key", DatabaseColumnType.STRING, primaryKey = true),
                DatabaseColumnSpec("title", DatabaseColumnType.STRING, nullable = false),
                DatabaseColumnSpec("durationMs", DatabaseColumnType.INT64, nullable = false)
            )
        )
        assertTrue(db.defineTable(schema))
        assertTrue(db.putRecord("tracks", DatabaseRecord("t1").withString("title", "Song One").withLong("durationMs", 180_000L)))
        assertTrue(db.exists("tracks", "t1"))
        assertEquals("Song One", db.getRecord("tracks", "t1")?.getString("title"))
        assertEquals(listOf("t1"), db.keys("tracks", "t"))
        assertEquals(1L, db.count(DatabaseQuery.from("tracks").where(DatabaseFilter.eq("title", "Song One"))))

        val writable = db.openWritable("blobs", "cover")
        writable.write(ByteBuffer.fromString("blob-payload"))
        writable.end()
        assertTrue(db.totalByteSize("blobs") > 0L)
        val readable = db.openReadable("blobs", "cover")
        assertNotNull(readable)
        assertEquals("blob-payload", readable.readAllText())
        readable.close()
        assertTrue(db.delete("tracks", "t1"))
        assertFalse(db.exists("tracks", "t1"))
        assertEquals(":memory:", JvmDatabaseBase.resolveDatabaseFilePath("Test", ":memory:"))
        db.dispose()

        val tempDir = File(System.getProperty("java.io.tmpdir", "."), "gui_res_base_${System.nanoTime()}").apply {
            mkdirs()
        }
        try {
            val localFile = File(tempDir, "shader.txt")
            localFile.writeText("precision highp float;")
            val resolver = object : JvmResourceResolverBase() {
                override fun findLocalResourceFile(resId: ResourceID): File? =
                    if (resId.name == "local_shader") localFile else super.findLocalResourceFile(resId)
            }
            val fileResId = ResourceID(0x7f079001, ResourceType.RAW, "local_shader", "raw/local_shader.txt")
            assertEquals("precision highp float;", resolver.resolveBytes(fileResId)?.decodeToString())
            assertTrue(resolver.resolveUrl(fileResId).startsWith("file:"))
            val fileStream = resolver.openResourceStream(fileResId)
            assertNotNull(fileStream)
            assertEquals("precision highp float;", fileStream.readAllText())
            fileStream.close()

            val memResId = ResourceID(0x7f079002, ResourceType.RAW, "mem_cfg", "raw/mem_cfg.json")
            Resources.activeTable.registerRawText(memResId, "{\"ok\":1}")
            val memStream = resolver.openResourceStream(memResId)
            assertNotNull(memStream)
            assertEquals("{\"ok\":1}", memStream.readAllText())
            memStream.close()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
