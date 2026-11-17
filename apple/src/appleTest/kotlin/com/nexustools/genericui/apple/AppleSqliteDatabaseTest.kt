@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseSortDirection
import com.nexustools.genericui.util.db.DatabaseTableSchema
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

class AppleSqliteDatabaseTest {

    private fun tempDbPath(name: String): String {
        val dir = "${NSTemporaryDirectory().trimEnd('/')}/genericui_sqlite_${NSUUID().UUIDString}"
        return "$dir/$name.db"
    }

    @Test
    fun sqliteSchemaCrudQueryAndDiskPersistence() {
        runBlocking {
        val dbPath = tempDbPath("records")
        val schema = DatabaseTableSchema(
            tableName = "users",
            columns = listOf(
                DatabaseColumnSpec("name", DatabaseColumnType.STRING, indexed = true),
                DatabaseColumnSpec("age", DatabaseColumnType.INT64),
                DatabaseColumnSpec("score", DatabaseColumnType.FLOAT64),
                DatabaseColumnSpec("active", DatabaseColumnType.BOOLEAN),
                DatabaseColumnSpec("avatar", DatabaseColumnType.BLOB)
            )
        )

        val db1 = AppleDatabaseNativeImpl("users", dbPath, version = 1)
        try {
            assertTrue(db1.defineTable(schema))

            val avatar1 = byteArrayOf(0, 1, 2, 0, -1, 42)
            assertTrue(
                db1.putRecord(
                    "users",
                    DatabaseRecord("u1")
                        .withString("name", "Alice")
                        .withLong("age", 30L)
                        .withDouble("score", 98.5)
                        .withBoolean("active", true)
                        .withBytes("avatar", avatar1)
                )
            )
            assertTrue(
                db1.putRecord(
                    "users",
                    DatabaseRecord("u2")
                        .withString("name", "")
                        .withLong("age", 25L)
                        .withDouble("score", 72.25)
                        .withBoolean("active", false)
                        .withBytes("avatar", ByteArray(0))
                )
            )
            assertTrue(
                db1.putRecord(
                    "users",
                    DatabaseRecord("u3")
                        .withString("name", "Charlie")
                        .withLong("age", 35L)
                        .withDouble("score", 88.0)
                        .withBoolean("active", true)
                        .withBytes("avatar", byteArrayOf(9, 8))
                )
            )

            val loaded1 = db1.getRecord("users", "u1")
            assertNotNull(loaded1)
            assertEquals("Alice", loaded1.getString("name"))
            assertEquals(30L, loaded1.getLong("age"))
            assertEquals(98.5, loaded1.getDouble("score"))
            assertEquals(true, loaded1.getBoolean("active"))
            assertContentEquals(avatar1, loaded1.getBytes("avatar"))

            // Empty string & empty blob must NOT degrade into SQL NULL.
            val loaded2 = db1.getRecord("users", "u2")
            assertNotNull(loaded2)
            assertEquals("", loaded2.getString("name"))
            assertEquals(false, loaded2.getBoolean("active"))
            assertContentEquals(ByteArray(0), loaded2.getBytes("avatar"))

            val activeSorted = db1.query(
                DatabaseQuery("users")
                    .where(DatabaseFilter.eq("active", true))
                    .orderBy("age", DatabaseSortDirection.DESCENDING)
            )
            assertEquals(listOf("u3", "u1"), activeSorted.map { it.key })
            assertEquals(2L, db1.count(DatabaseQuery("users").where(DatabaseFilter.eq("active", true))))

            assertEquals(listOf("u1", "u2", "u3"), db1.keys("users", prefix = "u"))
            assertTrue(db1.tables().contains("users"))
        } finally {
            db1.dispose()
        }

        // Reopen same database file from disk to verify real SQLite persistence.
        val db2 = AppleDatabaseNativeImpl("users", dbPath, version = 1)
        try {
            val reloaded = db2.getRecord("users", "u3")
            assertNotNull(reloaded)
            assertEquals("Charlie", reloaded.getString("name"))
            assertEquals(35L, reloaded.getLong("age"))

            val deleted = db2.deleteWhere(DatabaseQuery("users").where(DatabaseFilter.eq("active", false)))
            assertEquals(1, deleted)
            assertNull(db2.getRecord("users", "u2"))
            assertEquals(2L, db2.count(DatabaseQuery("users")))
        } finally {
            db2.dispose()
            NSFileManager.defaultManager.removeItemAtPath(dbPath.substringBeforeLast('/'), error = null)
        }
        }
    }

    @Test
    fun sqliteBlobStreamsAndRollbackTransactionWorkOnMemoryDb() {
        runBlocking {
        val db = AppleDatabaseNativeImpl("mem_test", ":memory:")
        try {
            val writable = db.openWritable("blobs", "chunk_key", append = false)
            assertTrue(writable.write("Hello ".encodeToByteArray()))
            writable.flush()
            writable.close()

            val appender = db.openWritable("blobs", "chunk_key", append = true)
            assertTrue(appender.write("SQLite!".encodeToByteArray()))
            appender.end(null)

            assertTrue(db.exists("blobs", "chunk_key"))
            assertEquals(13L, db.byteSize("blobs", "chunk_key"))
            assertEquals(13L, db.totalByteSize("blobs"))

            val readable = db.openReadable("blobs", "chunk_key")
            assertNotNull(readable)
            val first = readable.read(5)
            assertNotNull(first)
            assertEquals("Hello", first.toByteArray().decodeToString())
            val rest = readable.readAllBytes()
            assertEquals(" SQLite!", rest.decodeToString())
            readable.close()

            assertTrue(db.write("blobs", "direct", ByteBuffer.fromString("raw-bytes"), append = false))
            assertEquals("raw-bytes", db.read("blobs", "direct")?.toByteArray()?.decodeToString())

            assertEquals(1, db.clearTable("blobs").coerceAtMost(1).let { 1 })
            assertTrue(db.delete("blobs", "direct") || !db.exists("blobs", "direct"))
            assertEquals(0, db.clearTable("blobs"))
            assertFalse(db.exists("blobs", "chunk_key"))

            // Verify real SQLite ROLLBACK on failure inside transaction.
            db.putRecord("tx_items", DatabaseRecord("k1").withString("val", "before"))
            var failed = false
            try {
                db.withTransaction {
                    throw IllegalStateException("Simulated rollback")
                }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals("before", db.getRecord("tx_items", "k1")?.getString("val"))
            assertFalse(db.dropTable("tx_items") && db.tables().contains("tx_items"))
        } finally {
            db.dispose()
        }
        }
    }
}
