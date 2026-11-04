package com.nexustools.genericui.util

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.StreamUtil
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl
import com.nexustools.genericui.nativeimpl.system.DefaultDatabaseNativeImpl
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema

/**
 * Cross-platform 100% asynchronous, non-blocking structured & key-value [Database].
 *
 * Uses an enum/class-based query & filter AST ([DatabaseTableSchema], [DatabaseQuery], [DatabaseFilter],
 * [DatabaseRecord]) that requires **zero string query parsing**:
 * - **System Targets (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:android`, `:ios`)**: Backed by a SQLite
 *   database file on disk (resolved inside [ConfigDirectory] by default, or at any custom [filePath] via
 *   [setFilePath] / [com.nexustools.genericui.io.IFileSystem.openDatabase]).
 * - **Web (`:web`)**: Backed by browser `window.indexedDB` in a strictly isolated database namespace
 *   (`genericui_db_<databaseName>`) so it never collides with `ConfigDirectory` or `CacheDirectory`.
 */
class Database internal constructor(
    val databaseName: String,
    filePath: String?,
    val version: Int,
    val factory: NativeFactory?,
    private val nativeImplOverride: DatabaseNativeImpl?
) : Disposable {

    constructor(
        databaseName: String = GenericUI.applicationName,
        filePath: String? = null,
        version: Int = 1,
        factory: NativeFactory? = null
    ) : this(databaseName, filePath, version, factory, null)

    private var customFilePath: String? = filePath?.trim()?.takeIf { it.isNotEmpty() }
    private var boundFactory: NativeFactory? = null
    private var boundFilePath: String? = null
    private var backingNativeImpl: DatabaseNativeImpl? = nativeImplOverride

    /**
     * Explicit database file path on system targets, or `null` when using the default
     * [ConfigDirectory]-resolved path (`<ConfigDirectory>/<sanitizedDatabaseName>.db`).
     */
    var filePath: String?
        get() = customFilePath
        set(value) {
            val cleaned = value?.trim()?.takeIf { it.isNotEmpty() }
            if (cleaned != customFilePath) {
                customFilePath = cleaned
                if (nativeImplOverride == null) {
                    backingNativeImpl = null
                }
            }
        }

    /**
     * Sets a custom OS file path for this [Database] on system targets and returns `this` for chaining.
     */
    fun setFilePath(path: String): Database = apply {
        this.filePath = path
    }

    /**
     * Resolved file path for this [Database] (either [filePath] if explicitly configured, or
     * `<ConfigDirectory>/<sanitizedDatabaseName>.db`).
     */
    val resolvedFilePath: String
        get() {
            customFilePath?.let { return it }
            val cleanDbFile = buildDefaultDbFileName(databaseName)
            return ConfigDirectory(appName = GenericUI.applicationName, factory = factory).resolve(cleanDbFile)
        }

    internal val nativeImpl: DatabaseNativeImpl
        get() {
            nativeImplOverride?.let { return it }
            val targetPath = resolvedFilePath
            val active = factory ?: GenericUI.activeFactory
            val current = backingNativeImpl
            if (current == null || active !== boundFactory || targetPath != boundFilePath) {
                boundFactory = active
                boundFilePath = targetPath
                val created = active?.createDatabase(
                    databaseName = databaseName,
                    filePath = targetPath,
                    version = version
                ) ?: DefaultDatabaseNativeImpl(
                    databaseName = databaseName,
                    filePath = targetPath,
                    version = version
                )
                backingNativeImpl = created
                return created
            }
            return current
        }

    override val isDisposed: Boolean
        get() = backingNativeImpl?.isDisposed == true

    suspend fun defineTable(schema: DatabaseTableSchema): Boolean =
        nativeImpl.defineTable(schema)

    suspend fun putRecord(table: String, record: DatabaseRecord): Boolean =
        nativeImpl.putRecord(table, record)

    suspend fun putRecord(record: DatabaseRecord): Boolean =
        nativeImpl.putRecord(DEFAULT_TABLE, record)

    suspend fun getRecord(table: String, key: String): DatabaseRecord? =
        nativeImpl.getRecord(table, key)

    suspend fun getRecord(key: String): DatabaseRecord? =
        nativeImpl.getRecord(DEFAULT_TABLE, key)

    suspend fun query(query: DatabaseQuery): List<DatabaseRecord> =
        nativeImpl.query(query)

    suspend fun query(table: String, filter: DatabaseFilter? = null): List<DatabaseRecord> =
        nativeImpl.query(DatabaseQuery(table = table, filter = filter))

    suspend fun count(query: DatabaseQuery): Long =
        nativeImpl.count(query)

    suspend fun count(table: String = DEFAULT_TABLE, filter: DatabaseFilter? = null): Long =
        nativeImpl.count(DatabaseQuery(table = table, filter = filter))

    suspend fun deleteWhere(query: DatabaseQuery): Int =
        nativeImpl.deleteWhere(query)

    suspend fun deleteWhere(table: String, filter: DatabaseFilter): Int =
        nativeImpl.deleteWhere(DatabaseQuery(table = table, filter = filter))

    suspend fun read(table: String, key: String): ByteBuffer? =
        nativeImpl.read(table, key)

    suspend fun read(key: String): ByteBuffer? =
        nativeImpl.read(DEFAULT_TABLE, key)

    suspend fun readBytes(table: String, key: String): ByteArray? =
        read(table, key)?.toByteArray()

    suspend fun readBytes(key: String): ByteArray? =
        read(DEFAULT_TABLE, key)?.toByteArray()

    suspend fun readText(table: String, key: String): String? =
        read(table, key)?.toString("utf8")

    suspend fun readText(key: String): String? =
        read(DEFAULT_TABLE, key)?.toString("utf8")

    suspend fun write(table: String, key: String, buffer: ByteBuffer, append: Boolean = false): Boolean =
        nativeImpl.write(table, key, buffer, append)

    suspend fun write(key: String, buffer: ByteBuffer, append: Boolean = false): Boolean =
        nativeImpl.write(DEFAULT_TABLE, key, buffer, append)

    suspend fun writeBytes(table: String, key: String, bytes: ByteArray, append: Boolean = false): Boolean =
        write(table, key, ByteBuffer.wrap(bytes), append)

    suspend fun writeBytes(key: String, bytes: ByteArray, append: Boolean = false): Boolean =
        write(DEFAULT_TABLE, key, ByteBuffer.wrap(bytes), append)

    suspend fun writeText(table: String, key: String, text: String, append: Boolean = false): Boolean =
        write(table, key, ByteBuffer.fromString(text), append)

    suspend fun writeText(key: String, text: String, append: Boolean = false): Boolean =
        write(DEFAULT_TABLE, key, ByteBuffer.fromString(text), append)

    suspend fun write(table: String, key: String, readable: Readable, append: Boolean = false): Boolean {
        val writable = openWritable(table, key, append)
        StreamUtil.pipeline(readable, writable, closeBoth = true)
        return true
    }

    suspend fun openReadable(table: String, key: String): Readable? =
        nativeImpl.openReadable(table, key)

    suspend fun openReadable(key: String): Readable? =
        openReadable(DEFAULT_TABLE, key)

    suspend fun openWritable(table: String, key: String, append: Boolean = false): Writable =
        nativeImpl.openWritable(table, key, append)

    suspend fun openWritable(key: String, append: Boolean = false): Writable =
        openWritable(DEFAULT_TABLE, key, append)

    suspend fun exists(table: String, key: String): Boolean =
        nativeImpl.exists(table, key)

    suspend fun exists(key: String): Boolean =
        nativeImpl.exists(DEFAULT_TABLE, key)

    suspend fun byteSize(table: String, key: String): Long =
        nativeImpl.byteSize(table, key)

    suspend fun byteSize(key: String): Long =
        nativeImpl.byteSize(DEFAULT_TABLE, key)

    suspend fun delete(table: String, key: String): Boolean =
        nativeImpl.delete(table, key)

    suspend fun delete(key: String): Boolean =
        nativeImpl.delete(DEFAULT_TABLE, key)

    suspend fun keys(table: String = DEFAULT_TABLE, prefix: String = ""): List<String> =
        nativeImpl.keys(table, prefix)

    suspend fun clearTable(table: String = DEFAULT_TABLE): Int =
        nativeImpl.clearTable(table)

    suspend fun dropTable(table: String): Boolean =
        nativeImpl.dropTable(table)

    suspend fun tables(): List<String> =
        nativeImpl.tables()

    suspend fun totalByteSize(table: String = DEFAULT_TABLE): Long =
        nativeImpl.totalByteSize(table)

    override fun dispose() {
        backingNativeImpl?.dispose()
    }

    companion object {
        const val DEFAULT_TABLE: String = "records"

        private fun buildDefaultDbFileName(rawName: String): String {
            val sanitized = UserDirectory.sanitizeFileName(rawName.ifBlank { "database" })
            return if (sanitized.endsWith(".db", ignoreCase = true) ||
                sanitized.endsWith(".sqlite", ignoreCase = true) ||
                sanitized.endsWith(".sqlite3", ignoreCase = true)
            ) {
                sanitized
            } else {
                "$sanitized.db"
            }
        }
    }
}
