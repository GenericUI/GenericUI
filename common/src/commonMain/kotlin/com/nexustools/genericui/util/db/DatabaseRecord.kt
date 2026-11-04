package com.nexustools.genericui.util.db

import com.nexustools.genericui.io.ByteBuffer

/**
 * Structured row / record stored in a [com.nexustools.genericui.util.Database] table.
 */
class DatabaseRecord(
    val key: String,
    val values: Map<String, DatabaseValue> = emptyMap()
) {
    operator fun get(column: String): DatabaseValue? =
        if (column == "key" && !values.containsKey("key")) DatabaseValue.of(key) else values[column]

    fun getString(column: String): String? = get(column)?.asString()

    fun getLong(column: String): Long? = get(column)?.asLong()

    fun getInt(column: String): Int? = get(column)?.asInt()

    fun getDouble(column: String): Double? = get(column)?.asDouble()

    fun getFloat(column: String): Float? = get(column)?.asFloat()

    fun getBoolean(column: String): Boolean? = get(column)?.asBoolean()

    fun getBuffer(column: String = "value"): ByteBuffer? = get(column)?.asBuffer()

    fun getBytes(column: String = "value"): ByteArray? = get(column)?.asBytes()

    fun withValue(column: String, value: DatabaseValue): DatabaseRecord {
        val next = LinkedHashMap(values)
        next[column] = value
        return DatabaseRecord(key = if (column == "key") (value.asString() ?: key) else key, values = next)
    }

    fun withString(column: String, value: String?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withLong(column: String, value: Long?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withInt(column: String, value: Int?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withDouble(column: String, value: Double?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withBoolean(column: String, value: Boolean?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withBuffer(column: String, value: ByteBuffer?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun withBytes(column: String, value: ByteArray?): DatabaseRecord =
        withValue(column, DatabaseValue.of(value))

    fun project(selectedColumns: List<String>): DatabaseRecord {
        if (selectedColumns.isEmpty()) return this
        val filtered = LinkedHashMap<String, DatabaseValue>()
        for (col in selectedColumns) {
            val v = get(col)
            if (v != null) {
                filtered[col] = v
            }
        }
        return DatabaseRecord(key = key, values = filtered)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseRecord) return false
        return key == other.key && values == other.values
    }

    override fun hashCode(): Int = 31 * key.hashCode() + values.hashCode()

    override fun toString(): String = "DatabaseRecord(key='$key', values=$values)"

    companion object {
        fun ofKeyValue(key: String, buffer: ByteBuffer, updatedAt: Long = 0L): DatabaseRecord =
            DatabaseRecord(
                key = key,
                values = mapOf(
                    "key" to DatabaseValue.of(key),
                    "value" to DatabaseValue.of(buffer),
                    "updatedAt" to DatabaseValue.of(updatedAt)
                )
            )
    }
}
