package com.nexustools.genericui.util.db

import com.nexustools.genericui.io.ByteBuffer

/**
 * Structured, enum/class-based filter expression AST for [DatabaseQuery].
 *
 * Requires **zero string query parsing**: SQLite/JDBC backends translate the AST directly into
 * parameterized SQL via [DatabaseQuerySqlTranslator], while IndexedDB / in-memory backends evaluate
 * [matches] directly against each [DatabaseRecord].
 */
class DatabaseFilter private constructor(
    val column: String? = null,
    val comparisonOp: DatabaseComparisonOp? = null,
    val value: DatabaseValue = DatabaseValue.Null,
    val secondValue: DatabaseValue? = null,
    val values: List<DatabaseValue> = emptyList(),
    val logicalOp: DatabaseLogicalOp? = null,
    val children: List<DatabaseFilter> = emptyList()
) {

    val isLogicalGroup: Boolean
        get() = logicalOp != null

    infix fun and(other: DatabaseFilter): DatabaseFilter =
        and(this, other)

    infix fun or(other: DatabaseFilter): DatabaseFilter =
        or(this, other)

    /**
     * Evaluates this filter AST directly against [record] without any string query parsing.
     */
    fun matches(record: DatabaseRecord): Boolean {
        val logic = logicalOp
        if (logic != null) {
            if (children.isEmpty()) return true
            return when (logic) {
                DatabaseLogicalOp.AND -> children.all { it.matches(record) }
                DatabaseLogicalOp.OR -> children.any { it.matches(record) }
            }
        }

        val col = column ?: return true
        val op = comparisonOp ?: return true
        val actual: DatabaseValue = if (col == "key" && !record.values.containsKey("key")) {
            DatabaseValue.of(record.key)
        } else {
            record.values[col] ?: DatabaseValue.Null
        }

        return when (op) {
            DatabaseComparisonOp.IS_NULL -> actual.isNull
            DatabaseComparisonOp.IS_NOT_NULL -> !actual.isNull
            DatabaseComparisonOp.EQUALS -> !actual.isNull && actual == value
            DatabaseComparisonOp.NOT_EQUALS -> actual.isNull || actual != value
            DatabaseComparisonOp.LESS_THAN -> !actual.isNull && !value.isNull && actual < value
            DatabaseComparisonOp.LESS_THAN_OR_EQUAL -> !actual.isNull && !value.isNull && actual <= value
            DatabaseComparisonOp.GREATER_THAN -> !actual.isNull && !value.isNull && actual > value
            DatabaseComparisonOp.GREATER_THAN_OR_EQUAL -> !actual.isNull && !value.isNull && actual >= value
            DatabaseComparisonOp.STARTS_WITH -> {
                val s = actual.asString() ?: return false
                val prefix = value.asString() ?: return false
                s.startsWith(prefix)
            }
            DatabaseComparisonOp.CONTAINS -> {
                val s = actual.asString() ?: return false
                val needle = value.asString() ?: return false
                s.contains(needle)
            }
            DatabaseComparisonOp.IN_LIST -> !actual.isNull && values.any { it == actual }
            DatabaseComparisonOp.BETWEEN -> {
                val upper = secondValue ?: return false
                !actual.isNull && !value.isNull && !upper.isNull && actual >= value && actual <= upper
            }
        }
    }

    companion object {
        fun eq(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.EQUALS, value = value)

        fun eq(column: String, value: String): DatabaseFilter = eq(column, DatabaseValue.of(value))
        fun eq(column: String, value: Long): DatabaseFilter = eq(column, DatabaseValue.of(value))
        fun eq(column: String, value: Int): DatabaseFilter = eq(column, DatabaseValue.of(value))
        fun eq(column: String, value: Double): DatabaseFilter = eq(column, DatabaseValue.of(value))
        fun eq(column: String, value: Boolean): DatabaseFilter = eq(column, DatabaseValue.of(value))

        fun neq(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.NOT_EQUALS, value = value)

        fun neq(column: String, value: String): DatabaseFilter = neq(column, DatabaseValue.of(value))
        fun neq(column: String, value: Long): DatabaseFilter = neq(column, DatabaseValue.of(value))
        fun neq(column: String, value: Int): DatabaseFilter = neq(column, DatabaseValue.of(value))
        fun neq(column: String, value: Double): DatabaseFilter = neq(column, DatabaseValue.of(value))
        fun neq(column: String, value: Boolean): DatabaseFilter = neq(column, DatabaseValue.of(value))

        fun lt(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.LESS_THAN, value = value)

        fun lt(column: String, value: Long): DatabaseFilter = lt(column, DatabaseValue.of(value))
        fun lt(column: String, value: Int): DatabaseFilter = lt(column, DatabaseValue.of(value))
        fun lt(column: String, value: Double): DatabaseFilter = lt(column, DatabaseValue.of(value))
        fun lt(column: String, value: String): DatabaseFilter = lt(column, DatabaseValue.of(value))

        fun lte(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.LESS_THAN_OR_EQUAL, value = value)

        fun lte(column: String, value: Long): DatabaseFilter = lte(column, DatabaseValue.of(value))
        fun lte(column: String, value: Int): DatabaseFilter = lte(column, DatabaseValue.of(value))
        fun lte(column: String, value: Double): DatabaseFilter = lte(column, DatabaseValue.of(value))
        fun lte(column: String, value: String): DatabaseFilter = lte(column, DatabaseValue.of(value))

        fun gt(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.GREATER_THAN, value = value)

        fun gt(column: String, value: Long): DatabaseFilter = gt(column, DatabaseValue.of(value))
        fun gt(column: String, value: Int): DatabaseFilter = gt(column, DatabaseValue.of(value))
        fun gt(column: String, value: Double): DatabaseFilter = gt(column, DatabaseValue.of(value))
        fun gt(column: String, value: String): DatabaseFilter = gt(column, DatabaseValue.of(value))

        fun gte(column: String, value: DatabaseValue): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.GREATER_THAN_OR_EQUAL, value = value)

        fun gte(column: String, value: Long): DatabaseFilter = gte(column, DatabaseValue.of(value))
        fun gte(column: String, value: Int): DatabaseFilter = gte(column, DatabaseValue.of(value))
        fun gte(column: String, value: Double): DatabaseFilter = gte(column, DatabaseValue.of(value))
        fun gte(column: String, value: String): DatabaseFilter = gte(column, DatabaseValue.of(value))

        fun startsWith(column: String, prefix: String): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.STARTS_WITH, value = DatabaseValue.of(prefix))

        fun contains(column: String, substring: String): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.CONTAINS, value = DatabaseValue.of(substring))

        fun inList(column: String, values: List<DatabaseValue>): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.IN_LIST, values = values.toList())

        fun inStrings(column: String, values: Collection<String>): DatabaseFilter =
            inList(column, values.map { DatabaseValue.of(it) })

        fun inLongs(column: String, values: Collection<Long>): DatabaseFilter =
            inList(column, values.map { DatabaseValue.of(it) })

        fun inInts(column: String, values: Collection<Int>): DatabaseFilter =
            inList(column, values.map { DatabaseValue.of(it) })

        fun between(column: String, lowerInclusive: DatabaseValue, upperInclusive: DatabaseValue): DatabaseFilter =
            DatabaseFilter(
                column = column,
                comparisonOp = DatabaseComparisonOp.BETWEEN,
                value = lowerInclusive,
                secondValue = upperInclusive
            )

        fun between(column: String, lowerInclusive: Long, upperInclusive: Long): DatabaseFilter =
            between(column, DatabaseValue.of(lowerInclusive), DatabaseValue.of(upperInclusive))

        fun between(column: String, lowerInclusive: Int, upperInclusive: Int): DatabaseFilter =
            between(column, DatabaseValue.of(lowerInclusive), DatabaseValue.of(upperInclusive))

        fun between(column: String, lowerInclusive: Double, upperInclusive: Double): DatabaseFilter =
            between(column, DatabaseValue.of(lowerInclusive), DatabaseValue.of(upperInclusive))

        fun isNull(column: String): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.IS_NULL)

        fun isNotNull(column: String): DatabaseFilter =
            DatabaseFilter(column = column, comparisonOp = DatabaseComparisonOp.IS_NOT_NULL)

        fun and(vararg filters: DatabaseFilter): DatabaseFilter =
            DatabaseFilter(logicalOp = DatabaseLogicalOp.AND, children = filters.toList())

        fun or(vararg filters: DatabaseFilter): DatabaseFilter =
            DatabaseFilter(logicalOp = DatabaseLogicalOp.OR, children = filters.toList())

        fun columnValue(value: ByteBuffer): DatabaseValue = DatabaseValue.of(value)
    }
}
