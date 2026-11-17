package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.system.DefaultDatabaseNativeImpl
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseQuerySqlTranslator
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema
import com.nexustools.genericui.util.db.DatabaseValue
import java.io.File

/**
 * Shared JVM and Android (`minSdk >= 24`) base implementation of
 * [com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl] providing common record enrichment,
 * schema/column inference, [DatabaseReadableNativeImpl]/[DatabaseWritableNativeImpl] blob stream
 * adapters, [DefaultDatabaseNativeImpl] query/count fallbacks, and [JvmIoExecutor] dispatch for
 * Swing (`sqlite-jdbc`) and Android (`SQLiteDatabase`) subclasses.
 */
abstract class JvmDatabaseBase(
    databaseName: String = "GenericUI",
    filePath: String = "GenericUI.db",
    version: Int = 1,
    protected val isOnMainThread: () -> Boolean = { false }
) : DefaultDatabaseNativeImpl(databaseName, filePath, version) {

    protected val knownTableColumns = LinkedHashMap<String, LinkedHashMap<String, DatabaseColumnType>>()
    private var tickSequence: Long = 1700000000000L

    protected fun nextTickTimestamp(): Long = ++tickSequence

    protected suspend inline fun <T> runOnDatabaseIo(crossinline block: () -> T): T =
        JvmIoExecutor.runOnIo(isOnMainThread, block)

    override suspend fun getRecord(table: String, key: String): DatabaseRecord? {
        if (isDisposed) return null
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val selectByKeyQuery = DatabaseQuery.from(norm)
            .where(DatabaseFilter.eq(pkCol, key))
            .limit(1)
        return query(selectByKeyQuery).firstOrNull()
    }

    override suspend fun openReadable(table: String, key: String): DatabaseReadableNativeImpl? {
        val buf = read(table, key) ?: return null
        return DefaultDatabaseReadableNativeImpl(
            table = normalizeTableName(table),
            key = key,
            initialBytes = buf.toByteArray()
        )
    }

    override suspend fun openWritable(table: String, key: String, append: Boolean): DatabaseWritableNativeImpl {
        val initial = if (append) read(table, key)?.toByteArray() ?: ByteArray(0) else ByteArray(0)
        return DefaultDatabaseWritableNativeImpl(
            table = normalizeTableName(table),
            key = key,
            appendMode = append,
            initialBytes = initial
        ) { committed ->
            write(table, key, ByteBuffer.wrap(committed), append = false)
        }
    }

    override suspend fun exists(table: String, key: String): Boolean =
        getRecord(table, key) != null

    override suspend fun delete(table: String, key: String): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val deleted = deleteWhere(DatabaseQuery.from(norm).where(DatabaseFilter.eq(pkCol, key)))
        return deleted > 0
    }

    override suspend fun keys(table: String, prefix: String): List<String> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val q = if (prefix.isEmpty()) {
            DatabaseQuery.from(norm).select(pkCol)
        } else {
            DatabaseQuery.from(norm).select(pkCol).where(DatabaseFilter.startsWith(pkCol, prefix))
        }
        return query(q).map { it.key }
    }

    override suspend fun clearTable(table: String): Int =
        deleteWhere(DatabaseQuery.from(table))

    override suspend fun totalByteSize(table: String): Long {
        if (isDisposed) return 0L
        val records = query(DatabaseQuery.from(table))
        var total = 0L
        for (rec in records) {
            total += rec.getBuffer("value")?.size?.toLong() ?: 0L
        }
        return total
    }

    override fun dispose() {
        synchronized(this) {
            super.dispose()
            knownTableColumns.clear()
        }
    }

    protected fun registerSchemaColumns(
        normTable: String,
        schema: DatabaseTableSchema
    ): LinkedHashMap<String, DatabaseColumnType> {
        val colMap = knownTableColumns.getOrPut(normTable) { LinkedHashMap() }
        val effectiveCols = if (schema.columns.isEmpty()) {
            DatabaseTableSchema.defaultKeyValueColumns(schema.primaryKeyColumn)
        } else {
            schema.columns
        }
        for (col in effectiveCols) {
            colMap[col.name] = col.type
        }
        return colMap
    }

    protected fun enrichRecordForUpsert(
        normTable: String,
        record: DatabaseRecord
    ): Pair<String, DatabaseRecord> {
        val pkCol = schemas[normTable]?.primaryKeyColumn ?: "key"
        val enrichedValues = LinkedHashMap(record.values)
        if (!enrichedValues.containsKey(pkCol)) {
            enrichedValues[pkCol] = DatabaseValue.of(record.key)
        }
        if (!enrichedValues.containsKey("updatedAt")) {
            enrichedValues["updatedAt"] = DatabaseValue.of(nextTickTimestamp())
        }
        return pkCol to DatabaseRecord(key = record.key, values = enrichedValues)
    }

    protected fun buildOrLookupSchemaForRecord(
        normTable: String,
        pkCol: String,
        record: DatabaseRecord
    ): DatabaseTableSchema {
        val inferredSpecs = ArrayList<DatabaseColumnSpec>()
        inferredSpecs.add(
            DatabaseColumnSpec(
                name = pkCol,
                type = DatabaseColumnType.STRING,
                primaryKey = true,
                nullable = false
            )
        )
        for ((colName, value) in record.values) {
            if (colName == pkCol) continue
            inferredSpecs.add(
                DatabaseColumnSpec(
                    name = colName,
                    type = value.type ?: DatabaseColumnType.BLOB,
                    primaryKey = false,
                    nullable = true,
                    indexed = colName == "updatedAt"
                )
            )
        }
        val existing = schemas[normTable]
        val resolved = existing ?: DatabaseTableSchema(
            tableName = normTable,
            primaryKeyColumn = pkCol,
            columns = inferredSpecs
        )
        if (existing == null) {
            schemas[normTable] = resolved
        }
        val map = knownTableColumns.getOrPut(normTable) { LinkedHashMap() }
        for (spec in resolved.columns) {
            map[spec.name] = spec.type
        }
        return resolved
    }

    protected fun collectMissingColumnsForRecord(
        normTable: String,
        record: DatabaseRecord,
        existingColumnsLoader: () -> LinkedHashMap<String, DatabaseColumnType> = { LinkedHashMap() }
    ): LinkedHashMap<String, DatabaseColumnType> {
        val colMap = knownTableColumns.getOrPut(normTable) { existingColumnsLoader() }
        val newCols = LinkedHashMap<String, DatabaseColumnType>()
        for ((colName, value) in record.values) {
            val sanitized = DatabaseQuerySqlTranslator.sanitizeIdentifier(colName)
            val alreadyPresent = colMap.keys.any {
                DatabaseQuerySqlTranslator.sanitizeIdentifier(it).equals(sanitized, ignoreCase = true)
            }
            val valueType = value.type
            val resolvedType = valueType ?: colMap[colName] ?: DatabaseColumnType.BLOB
            if (!alreadyPresent) {
                newCols[colName] = resolvedType
            }
            if (valueType != null) {
                colMap[colName] = valueType
            }
        }
        return newCols
    }

    protected fun buildSchemaColumnLookup(
        normTable: String,
        extraColumns: Map<String, DatabaseColumnType> = emptyMap()
    ): LinkedHashMap<String, Pair<String, DatabaseColumnType>> {
        val schemaColBySanitized = LinkedHashMap<String, Pair<String, DatabaseColumnType>>()
        schemas[normTable]?.columns?.forEach { spec ->
            schemaColBySanitized[DatabaseQuerySqlTranslator.sanitizeIdentifier(spec.name)] = spec.name to spec.type
        }
        knownTableColumns[normTable]?.forEach { (k, t) ->
            val s = DatabaseQuerySqlTranslator.sanitizeIdentifier(k)
            if (!schemaColBySanitized.containsKey(s)) {
                schemaColBySanitized[s] = k to t
            }
        }
        for ((k, t) in extraColumns) {
            val s = DatabaseQuerySqlTranslator.sanitizeIdentifier(k)
            if (!schemaColBySanitized.containsKey(s)) {
                schemaColBySanitized[s] = k to t
            }
        }
        return schemaColBySanitized
    }

    protected fun mapSqliteAffinityToColumnType(typeStr: String): DatabaseColumnType {
        val upper = typeStr.uppercase()
        return when {
            upper.contains("INT") -> DatabaseColumnType.INT64
            upper.contains("REAL") || upper.contains("FLOA") || upper.contains("DOUB") ->
                DatabaseColumnType.FLOAT64
            upper.contains("BLOB") -> DatabaseColumnType.BLOB
            else -> DatabaseColumnType.STRING
        }
    }

    protected fun queryInMemoryFallback(query: DatabaseQuery): List<DatabaseRecord> {
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return emptyList()
        return query.applyToRecords(store.values)
    }

    protected fun countInMemoryFallback(query: DatabaseQuery): Long {
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return 0L
        val activeFilter = query.filter ?: return store.size.toLong()
        return store.values.count { activeFilter.matches(it) }.toLong()
    }

    companion object {
        fun resolveDatabaseFilePath(
            databaseName: String,
            rawFilePath: String,
            fallbackFolderName: String = "genericui_db"
        ): String {
            val trimmed = rawFilePath.trim()
            if (trimmed == ":memory:") return ":memory:"
            if (trimmed.startsWith("jdbc:sqlite:")) return trimmed.removePrefix("jdbc:sqlite:")

            val tmpRoot = File(System.getProperty("java.io.tmpdir", "."), fallbackFolderName)
            val candidate = if (trimmed.contains("://")) {
                val safe = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
                File(tmpRoot, safe)
            } else if (File.separatorChar == '\\' && trimmed.startsWith("/") && !(trimmed.length >= 3 && trimmed[2] == ':')) {
                File(tmpRoot, trimmed.trimStart('/'))
            } else {
                val direct = File(trimmed)
                if (direct.isAbsolute) direct else File(tmpRoot, trimmed)
            }

            val ensured = runCatching {
                candidate.parentFile?.mkdirs()
                candidate.absolutePath
            }.getOrNull()
            if (ensured != null) return ensured

            val fallbackName = databaseName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "GenericUI" }
            val tempFallback = File(tmpRoot, "$fallbackName.db")
            runCatching { tempFallback.parentFile?.mkdirs() }
            return tempFallback.absolutePath
        }
    }
}
