package com.nexustools.genericui.util.db

import com.nexustools.genericui.io.ByteBuffer

/**
 * Typed column value stored in a [DatabaseRecord] or bound as a parameter in a [DatabaseFilter].
 */
class DatabaseValue private constructor(
    val type: DatabaseColumnType?,
    val stringValue: String? = null,
    val longValue: Long? = null,
    val doubleValue: Double? = null,
    val booleanValue: Boolean? = null,
    val blobValue: ByteBuffer? = null
) : Comparable<DatabaseValue> {

    val isNull: Boolean
        get() = type == null

    fun asString(): String? = when (type) {
        DatabaseColumnType.STRING -> stringValue
        DatabaseColumnType.INT64 -> longValue?.toString()
        DatabaseColumnType.FLOAT64 -> doubleValue?.toString()
        DatabaseColumnType.BOOLEAN -> booleanValue?.toString()
        DatabaseColumnType.BLOB -> blobValue?.toString("utf8")
        null -> null
    }

    fun asLong(): Long? = when (type) {
        DatabaseColumnType.INT64 -> longValue
        DatabaseColumnType.FLOAT64 -> doubleValue?.toLong()
        DatabaseColumnType.BOOLEAN -> if (booleanValue == true) 1L else 0L
        DatabaseColumnType.STRING -> stringValue?.toLongOrNull()
        else -> null
    }

    fun asInt(): Int? = asLong()?.toInt()

    fun asDouble(): Double? = when (type) {
        DatabaseColumnType.FLOAT64 -> doubleValue
        DatabaseColumnType.INT64 -> longValue?.toDouble()
        DatabaseColumnType.BOOLEAN -> if (booleanValue == true) 1.0 else 0.0
        DatabaseColumnType.STRING -> stringValue?.toDoubleOrNull()
        else -> null
    }

    fun asFloat(): Float? = asDouble()?.toFloat()

    fun asBoolean(): Boolean? = when (type) {
        DatabaseColumnType.BOOLEAN -> booleanValue
        DatabaseColumnType.INT64 -> (longValue ?: 0L) != 0L
        DatabaseColumnType.FLOAT64 -> (doubleValue ?: 0.0) != 0.0
        DatabaseColumnType.STRING -> when (stringValue?.trim()?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        else -> null
    }

    fun asBuffer(): ByteBuffer? = when (type) {
        DatabaseColumnType.BLOB -> blobValue?.copy()
        DatabaseColumnType.STRING -> stringValue?.let { ByteBuffer.fromString(it) }
        else -> null
    }

    fun asBytes(): ByteArray? = asBuffer()?.toByteArray()

    override fun compareTo(other: DatabaseValue): Int {
        if (this.isNull && other.isNull) return 0
        if (this.isNull) return -1
        if (other.isNull) return 1

        if ((this.type == DatabaseColumnType.INT64 || this.type == DatabaseColumnType.FLOAT64) &&
            (other.type == DatabaseColumnType.INT64 || other.type == DatabaseColumnType.FLOAT64)
        ) {
            if (this.type == DatabaseColumnType.INT64 && other.type == DatabaseColumnType.INT64) {
                return (this.longValue ?: 0L).compareTo(other.longValue ?: 0L)
            }
            return (this.asDouble() ?: 0.0).compareTo(other.asDouble() ?: 0.0)
        }

        return when (this.type) {
            DatabaseColumnType.STRING -> (this.stringValue ?: "").compareTo(other.asString() ?: "")
            DatabaseColumnType.BOOLEAN -> (this.booleanValue ?: false).compareTo(other.asBoolean() ?: false)
            DatabaseColumnType.BLOB -> {
                val a = this.blobValue?.toByteArray() ?: ByteArray(0)
                val b = other.asBytes() ?: ByteArray(0)
                val minLen = minOf(a.size, b.size)
                for (i in 0 until minLen) {
                    val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
                    if (cmp != 0) return cmp
                }
                a.size.compareTo(b.size)
            }
            else -> (this.asString() ?: "").compareTo(other.asString() ?: "")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseValue) return false
        if (this.isNull && other.isNull) return true
        if (this.isNull || other.isNull) return false
        if ((this.type == DatabaseColumnType.INT64 || this.type == DatabaseColumnType.FLOAT64) &&
            (other.type == DatabaseColumnType.INT64 || other.type == DatabaseColumnType.FLOAT64)
        ) {
            if (this.type == DatabaseColumnType.INT64 && other.type == DatabaseColumnType.INT64) {
                return this.longValue == other.longValue
            }
            return this.asDouble() == other.asDouble()
        }
        if (this.type != other.type) return false
        return when (this.type) {
            null -> true
            DatabaseColumnType.STRING -> this.stringValue == other.stringValue
            DatabaseColumnType.INT64 -> this.longValue == other.longValue
            DatabaseColumnType.FLOAT64 -> this.doubleValue == other.doubleValue
            DatabaseColumnType.BOOLEAN -> this.booleanValue == other.booleanValue
            DatabaseColumnType.BLOB -> this.blobValue == other.blobValue
        }
    }

    override fun hashCode(): Int = when (type) {
        null -> 0
        DatabaseColumnType.STRING -> stringValue.hashCode()
        DatabaseColumnType.INT64 -> (longValue ?: 0L).hashCode()
        DatabaseColumnType.FLOAT64 -> (doubleValue ?: 0.0).hashCode()
        DatabaseColumnType.BOOLEAN -> (booleanValue ?: false).hashCode()
        DatabaseColumnType.BLOB -> blobValue.hashCode()
    }

    override fun toString(): String = when (type) {
        null -> "NULL"
        DatabaseColumnType.STRING -> "'${stringValue ?: ""}'"
        DatabaseColumnType.INT64 -> (longValue ?: 0L).toString()
        DatabaseColumnType.FLOAT64 -> (doubleValue ?: 0.0).toString()
        DatabaseColumnType.BOOLEAN -> (booleanValue ?: false).toString()
        DatabaseColumnType.BLOB -> "BLOB(${blobValue?.size ?: 0} bytes)"
    }

    companion object {
        val Null: DatabaseValue = DatabaseValue(type = null)

        fun of(value: String?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.STRING, stringValue = value)

        fun of(value: Long?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.INT64, longValue = value)

        fun of(value: Int?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.INT64, longValue = value.toLong())

        fun of(value: Double?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.FLOAT64, doubleValue = value)

        fun of(value: Float?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.FLOAT64, doubleValue = value.toDouble())

        fun of(value: Boolean?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.BOOLEAN, booleanValue = value)

        fun of(value: ByteArray?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.BLOB, blobValue = ByteBuffer.wrap(value))

        fun of(value: ByteBuffer?): DatabaseValue =
            if (value == null) Null else DatabaseValue(DatabaseColumnType.BLOB, blobValue = value.copy())

        fun fromAny(value: Any?): DatabaseValue = when (value) {
            null -> Null
            is DatabaseValue -> value
            is String -> of(value)
            is Long -> of(value)
            is Int -> of(value)
            is Short -> of(value.toLong())
            is Byte -> of(value.toLong())
            is Double -> of(value)
            is Float -> of(value)
            is Boolean -> of(value)
            is ByteArray -> of(value)
            is ByteBuffer -> of(value)
            else -> of(value.toString())
        }
    }
}
