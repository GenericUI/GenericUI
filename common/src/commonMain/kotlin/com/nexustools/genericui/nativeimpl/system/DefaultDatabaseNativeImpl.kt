package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseWritableNativeImpl
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema
import com.nexustools.genericui.util.db.DatabaseValue

/**
 * Cross-platform in-memory fallback implementation of [DatabaseNativeImpl] used when no native
 * backend factory is active or during headless unit tests.
 *
 * Evaluates [DatabaseQuery] and [com.nexustools.genericui.util.db.DatabaseFilter] ASTs directly in memory
 * with zero string query parsing.
 */
open class DefaultDatabaseNativeImpl(
    override val databaseName: String = "GenericUI",
    override val filePath: String = "GenericUI.db",
    override val version: Int = 1
) : DatabaseNativeImpl {

    protected val schemas = LinkedHashMap<String, DatabaseTableSchema>()
    protected val tableRecords = LinkedHashMap<String, LinkedHashMap<String, DatabaseRecord>>()
    private var tickCounter: Long = 1700000000000L

    override var isDisposed: Boolean = false
        protected set

    override suspend fun defineTable(schema: DatabaseTableSchema): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(schema.tableName)
        schemas[norm] = schema
        tableRecords.getOrPut(norm) { LinkedHashMap() }
        return true
    }

    override suspend fun putRecord(table: String, record: DatabaseRecord): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        val store = tableRecords.getOrPut(norm) { LinkedHashMap() }
        val enrichedValues = LinkedHashMap(record.values)
        if (!enrichedValues.containsKey("key")) {
            enrichedValues["key"] = DatabaseValue.of(record.key)
        }
        if (!enrichedValues.containsKey("updatedAt")) {
            enrichedValues["updatedAt"] = DatabaseValue.of(++tickCounter)
        }
        store[record.key] = DatabaseRecord(key = record.key, values = enrichedValues)
        return true
    }

    override suspend fun getRecord(table: String, key: String): DatabaseRecord? {
        if (isDisposed) return null
        val norm = normalizeTableName(table)
        return tableRecords[norm]?.get(key)
    }

    override suspend fun query(query: DatabaseQuery): List<DatabaseRecord> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return emptyList()
        return query.applyToRecords(store.values)
    }

    override suspend fun count(query: DatabaseQuery): Long {
        if (isDisposed) return 0L
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return 0L
        val activeFilter = query.filter ?: return store.size.toLong()
        return store.values.count { activeFilter.matches(it) }.toLong()
    }

    override suspend fun deleteWhere(query: DatabaseQuery): Int {
        if (isDisposed) return 0
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return 0
        val activeFilter = query.filter
        if (activeFilter == null) {
            val removed = store.size
            store.clear()
            return removed
        }
        val matchingKeys = store.values.filter { activeFilter.matches(it) }.map { it.key }
        for (k in matchingKeys) {
            store.remove(k)
        }
        return matchingKeys.size
    }

    override suspend fun read(table: String, key: String): ByteBuffer? {
        val record = getRecord(table, key) ?: return null
        return record.getBuffer("value")
    }

    override suspend fun write(table: String, key: String, buffer: ByteBuffer, append: Boolean): Boolean {
        if (isDisposed) return false
        val payload = if (append) {
            val existing = read(table, key)?.toByteArray() ?: ByteArray(0)
            val incoming = buffer.toByteArray()
            ByteBuffer.wrap(if (existing.isEmpty()) incoming else existing + incoming)
        } else {
            buffer.copy()
        }
        val existingRecord = getRecord(table, key)
        val nextValues = LinkedHashMap<String, DatabaseValue>()
        if (existingRecord != null) {
            nextValues.putAll(existingRecord.values)
        }
        nextValues["key"] = DatabaseValue.of(key)
        nextValues["value"] = DatabaseValue.of(payload)
        nextValues["updatedAt"] = DatabaseValue.of(++tickCounter)
        return putRecord(table, DatabaseRecord(key = key, values = nextValues))
    }

    override suspend fun openReadable(table: String, key: String): DatabaseReadableNativeImpl? {
        val buf = read(table, key) ?: return null
        return DefaultDatabaseReadableNativeImpl(
            table = table,
            key = key,
            initialBytes = buf.toByteArray()
        )
    }

    override suspend fun openWritable(table: String, key: String, append: Boolean): DatabaseWritableNativeImpl {
        val initial = if (append) read(table, key)?.toByteArray() ?: ByteArray(0) else ByteArray(0)
        return DefaultDatabaseWritableNativeImpl(
            table = table,
            key = key,
            appendMode = append,
            initialBytes = initial
        ) { committed ->
            write(table, key, ByteBuffer.wrap(committed), append = false)
        }
    }

    override suspend fun exists(table: String, key: String): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        return tableRecords[norm]?.containsKey(key) == true
    }

    override suspend fun byteSize(table: String, key: String): Long =
        read(table, key)?.size?.toLong() ?: 0L

    override suspend fun delete(table: String, key: String): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        return tableRecords[norm]?.remove(key) != null
    }

    override suspend fun keys(table: String, prefix: String): List<String> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(table)
        val store = tableRecords[norm] ?: return emptyList()
        if (prefix.isEmpty()) return store.keys.toList()
        return store.keys.filter { it.startsWith(prefix) }
    }

    override suspend fun clearTable(table: String): Int {
        if (isDisposed) return 0
        val norm = normalizeTableName(table)
        val store = tableRecords[norm] ?: return 0
        val count = store.size
        store.clear()
        return count
    }

    override suspend fun dropTable(table: String): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        schemas.remove(norm)
        return tableRecords.remove(norm) != null
    }

    override suspend fun tables(): List<String> {
        if (isDisposed) return emptyList()
        val all = LinkedHashSet<String>()
        all.addAll(schemas.keys)
        all.addAll(tableRecords.keys)
        return all.toList()
    }

    override suspend fun totalByteSize(table: String): Long {
        if (isDisposed) return 0L
        val norm = normalizeTableName(table)
        val store = tableRecords[norm] ?: return 0L
        var total = 0L
        for (rec in store.values) {
            total += rec.getBuffer("value")?.size?.toLong() ?: 0L
        }
        return total
    }

    override fun dispose() {
        isDisposed = true
        schemas.clear()
        tableRecords.clear()
    }

    protected fun normalizeTableName(raw: String): String =
        raw.trim().ifEmpty { "default" }
}
