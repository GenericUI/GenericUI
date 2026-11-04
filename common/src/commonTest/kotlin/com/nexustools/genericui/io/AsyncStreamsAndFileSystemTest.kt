package com.nexustools.genericui.io

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.ConfigDirectory
import com.nexustools.genericui.util.Database
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseQuerySqlTranslator
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseSortDirection
import com.nexustools.genericui.util.db.DatabaseTableSchema
import com.nexustools.genericui.util.db.DatabaseValue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncStreamsAndFileSystemTest {

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        var completed = false
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context: CoroutineContext = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) {
                    completed = true
                    failure = result.exceptionOrNull()
                }
            }
        )
        failure?.let { throw it }
        assertTrue(completed, "Expected suspend test block to complete without hanging")
    }

    @Test
    fun `ByteBuffer WritableByteBuffer and ReadableByteBuffer read and write primitives and strings`() = runSuspend {
        val buf = ByteBuffer.allocate(32)
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.byteOrder)
        buf.putIntBE(0x12345678)
        buf.putIntLE(0x78563412)
        buf.putLongBE(9876543210123L)
        buf.putDoubleBE(3.1415926535)
        buf.flip()

        assertEquals(0x12345678, buf.getIntBE())
        assertEquals(0x78563412, buf.getIntLE())
        assertEquals(9876543210123L, buf.getLongBE())
        assertEquals(3.1415926535, buf.getDoubleBE(), 1e-9)
        assertFalse(buf.hasRemaining)

        val textBuf = ByteBuffer.fromString("GenericUI Async IO")
        assertEquals("GenericUI Async IO", textBuf.toString("utf8"))
        assertEquals("GenericUI", textBuf.slice(0, 9).toString())

        val writer = WritableByteBuffer(initialCapacity = 16, byteOrder = ByteOrder.LITTLE_ENDIAN)
        assertIs<Writable>(writer)
        writer.writeByte(0x2A)
        writer.writeBoolean(true)
        writer.writeShort(1024, ByteOrder.BIG_ENDIAN)
        writer.writeInt(987654321)
        writer.writeLong(1122334455667788L, ByteOrder.BIG_ENDIAN)
        writer.writeFloat(2.5f)
        writer.writeDouble(6.283185307)
        writer.writeString("StreamTail")

        val reader = ReadableByteBuffer(writer.toByteBuffer())
        assertIs<Readable>(reader)
        assertEquals(0x2A.toByte(), reader.readByte())
        assertTrue(reader.readBoolean())
        assertEquals(1024.toShort(), reader.readShort(ByteOrder.BIG_ENDIAN))
        assertEquals(987654321, reader.readInt())
        assertEquals(1122334455667788L, reader.readLong(ByteOrder.BIG_ENDIAN))
        assertEquals(2.5f, reader.readFloat())
        assertEquals(6.283185307, reader.readDouble(), 1e-9)
        assertEquals("StreamTail", reader.readString(10))
    }

    @Test
    fun `Readable Writable and StreamUtil pipeline transfer chunks and lines asynchronously`() = runSuspend {
        val inputText = "alpha\nbeta\ngamma\ndelta"
        val readable = Readable.fromText(inputText)
        val lines = mutableListOf<String>()
        readable.forEachLine { lines.add(it) }
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), lines)

        var committedBytes: ByteArray? = null
        val source = Readable.fromText("Hello, non-blocking world!")
        val sink = Writable.toMemory { bytes -> committedBytes = bytes }
        val pipedCount = StreamUtil.pipeline(source, sink, closeBoth = true)

        assertEquals(26L, pipedCount)
        assertTrue(sink.isFinished)
        assertNotNull(committedBytes)
        assertEquals("Hello, non-blocking world!", ByteBuffer.wrap(committedBytes!!).toString())

        val transformSource = Readable.fromText("genericui")
        val transformSink = Writable.toMemory()
        StreamUtil.transform(transformSource, transformSink) { chunk ->
            chunk.decodeToString().uppercase().encodeToByteArray()
        }
        assertEquals("GENERICUI", transformSink.toByteBuffer().toString())
    }

    @Test
    fun `FileSystem and UserDirectory implement shared IFileSystem contract with openDatabase`() = runSuspend {
        FileSystem.resetFallback()
        GenericUI.applicationName = "AsyncFsTestApp"

        assertEquals(FileSystemAvailability.Always, FileSystem.isAvailable())
        val resolvedPath = FileSystem.resolve("/projects/demo", "assets", "../config/app.json")
        assertEquals("/projects/demo/config/app.json", resolvedPath)

        assertTrue(FileSystem.write(resolvedPath, ByteBuffer.fromString("{\"version\":1}")))
        assertTrue(FileSystem.exists(resolvedPath))
        assertEquals("{\"version\":1}", FileSystem.read(resolvedPath)?.toString())

        val writable = FileSystem.openWritable("/projects/demo/streamed.txt", append = false)
        writable.writeText("Line 1\n")
        writable.endText("Line 2")

        val readable = FileSystem.openReadable("/projects/demo/streamed.txt")
        assertNotNull(readable)
        assertEquals("Line 1\nLine 2", readable.readAllText())

        val fsDb = FileSystem.openDatabase("/projects/demo/local_store.db", version = 2)
        assertEquals("/projects/demo/local_store.db", fsDb.resolvedFilePath)
        assertEquals(2, fsDb.version)
        assertTrue(fsDb.writeText("greeting", "Hello from FileSystem DB"))
        assertEquals("Hello from FileSystem DB", fsDb.readText("greeting"))
        fsDb.dispose()

        val configDir = ConfigDirectory(appName = "AsyncFsTestApp", subdirectory = "prefs")
        val cacheDir = CacheDirectory(appName = "AsyncFsTestApp", subdirectory = "http")
        val systems: List<IFileSystem> = listOf(FileSystem, configDir, cacheDir)
        for (fs in systems) {
            assertEquals(FileSystemAvailability.Always, fs.isAvailable())
        }

        val cfgFile = configDir.resolve("window.json")
        assertTrue(configDir.write(cfgFile, ByteBuffer.fromString("{\"maximized\":true}")))
        assertEquals("{\"maximized\":true}", configDir.read("window.json")?.toString())

        val cfgStream = configDir.openWritable("recent.txt", append = false)
        cfgStream.endText("project-1.gui")
        assertEquals("project-1.gui", configDir.openReadable("recent.txt")?.readAllText())

        val cfgDb = configDir.openDatabase("preferences", version = 3)
        assertTrue(cfgDb.resolvedFilePath.endsWith("preferences.db"))
        assertTrue(cfgDb.writeText("theme", "dark"))
        assertEquals("dark", cfgDb.readText("theme"))
        cfgDb.dispose()
        configDir.dispose()
        cacheDir.dispose()
    }

    @Test
    fun `Database executes structured enum and class queries and translates parameterized SQL without string parsing`() = runSuspend {
        val db = Database(databaseName = "CatalogTestDb", version = 1)
        val schema = DatabaseTableSchema(
            tableName = "items",
            primaryKeyColumn = "key",
            columns = listOf(
                DatabaseColumnSpec("key", DatabaseColumnType.STRING, primaryKey = true),
                DatabaseColumnSpec("title", DatabaseColumnType.STRING, nullable = false, indexed = true),
                DatabaseColumnSpec("category", DatabaseColumnType.STRING, nullable = false, indexed = true),
                DatabaseColumnSpec("priceCents", DatabaseColumnType.INT64, nullable = false, indexed = true),
                DatabaseColumnSpec("inStock", DatabaseColumnType.BOOLEAN, nullable = false)
            )
        )
        assertTrue(db.defineTable(schema))

        val ddlStatements = DatabaseQuerySqlTranslator.buildCreateTableSql(schema)
        assertTrue(ddlStatements.first().sql.startsWith("CREATE TABLE IF NOT EXISTS \"items\""))
        assertTrue(ddlStatements.any { it.sql.contains("CREATE INDEX IF NOT EXISTS") })

        val sampleItems = listOf(
            DatabaseRecord("item_1")
                .withString("title", "Pro Canvas Brush Pack")
                .withString("category", "brushes")
                .withLong("priceCents", 1500L)
                .withBoolean("inStock", true),
            DatabaseRecord("item_2")
                .withString("title", "Pro Vector Icons")
                .withString("category", "icons")
                .withLong("priceCents", 2500L)
                .withBoolean("inStock", true),
            DatabaseRecord("item_3")
                .withString("title", "Starter Palette")
                .withString("category", "palettes")
                .withLong("priceCents", 500L)
                .withBoolean("inStock", false),
            DatabaseRecord("item_4")
                .withString("title", "Pro Shader Filters")
                .withString("category", "filters")
                .withLong("priceCents", 3500L)
                .withBoolean("inStock", true)
        )
        for (rec in sampleItems) {
            assertTrue(db.putRecord("items", rec))
        }
        assertEquals(4L, db.count("items"))

        val filter = DatabaseFilter.and(
            DatabaseFilter.startsWith("title", "Pro "),
            DatabaseFilter.eq("inStock", true),
            DatabaseFilter.between("priceCents", 1000L, 3000L),
            DatabaseFilter.inStrings("category", listOf("brushes", "icons", "filters"))
        )
        val query = DatabaseQuery.from("items")
            .where(filter)
            .orderBy("priceCents", DatabaseSortDirection.DESCENDING)
            .limit(10)

        val prepared = DatabaseQuerySqlTranslator.buildSelectSql(query)
        assertTrue(prepared.sql.contains("SELECT * FROM \"items\" WHERE"))
        assertTrue(prepared.sql.contains("ORDER BY \"priceCents\" DESC LIMIT 10"))
        assertTrue(prepared.bindValues.contains(DatabaseValue.of("Pro %")))

        val matches = db.query(query)
        assertEquals(2, matches.size)
        assertEquals("item_2", matches[0].key)
        assertEquals("item_1", matches[1].key)

        val blobWriter = db.openWritable("blobs", "thumbnail_1")
        blobWriter.write(byteArrayOf(1, 2, 3, 4, 5))
        blobWriter.end()
        val blobReader = db.openReadable("blobs", "thumbnail_1")
        assertNotNull(blobReader)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), blobReader.readAllBytes().toList())

        val deleted = db.deleteWhere(
            DatabaseQuery.from("items").where(DatabaseFilter.eq("inStock", false))
        )
        assertEquals(1, deleted)
        assertNull(db.getRecord("items", "item_3"))
        assertEquals(3L, db.count("items"))

        db.dispose()
    }

    @Test
    fun `ResourceID openReadable reads compiled resource payload as ReadableByteBuffer`() = runSuspend {
        val rawResId = ResourceID(0x7f070099, ResourceType.RAW, "sample_shader", "raw/sample_shader.glsl")
        Resources.activeTable.registerRawText(rawResId, "void main() { gl_Position = vec4(0.0); }")

        val stream = rawResId.openReadable()
        assertNotNull(stream)
        assertIs<ReadableByteBuffer>(stream)
        val content = stream.readAllText()
        assertEquals("void main() { gl_Position = vec4(0.0); }", content)
    }
}
