package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema

/**
 * Per-backend native contract for a 100% asynchronous, non-blocking structured [com.nexustools.genericui.util.Database].
 *
 * Platform implementations:
 * - **Desktop (`:windows`, `:macos`, `:gtk`, `:qt`) & `:ios`**: Native `sqlite3` C library executing
 *   [com.nexustools.genericui.util.db.DatabaseQuerySqlTranslator] parameterized SQL on an async coroutine dispatcher.
 * - **Swing (`:swing`)**: JDBC SQLite (`org.sqlite.JDBC`) / file-backed engine on `Dispatchers.IO`.
 * - **Android (`:android`)**: `android.database.sqlite.SQLiteDatabase` on `Dispatchers.IO`.
 * - **Web (`:web`)**: Browser `window.indexedDB` object stores & indexes in a dedicated database namespace
 *   (`genericui_db_<databaseName>`), completely isolated from `ConfigDirectory` and `CacheDirectory`.
 */
interface DatabaseNativeImpl : Disposable {
    val databaseName: String
    val filePath: String
    val version: Int

    suspend fun defineTable(schema: DatabaseTableSchema): Boolean

    suspend fun putRecord(table: String, record: DatabaseRecord): Boolean

    suspend fun getRecord(table: String, key: String): DatabaseRecord?

    suspend fun query(query: DatabaseQuery): List<DatabaseRecord>

    suspend fun count(query: DatabaseQuery): Long

    suspend fun deleteWhere(query: DatabaseQuery): Int

    suspend fun read(table: String, key: String): ByteBuffer?

    suspend fun write(table: String, key: String, buffer: ByteBuffer, append: Boolean = false): Boolean

    suspend fun openReadable(table: String, key: String): DatabaseReadableNativeImpl?

    suspend fun openWritable(table: String, key: String, append: Boolean = false): DatabaseWritableNativeImpl

    suspend fun exists(table: String, key: String): Boolean

    suspend fun byteSize(table: String, key: String): Long

    suspend fun delete(table: String, key: String): Boolean

    suspend fun keys(table: String, prefix: String = ""): List<String>

    suspend fun clearTable(table: String): Int

    suspend fun dropTable(table: String): Boolean

    suspend fun tables(): List<String>

    suspend fun totalByteSize(table: String): Long
}
