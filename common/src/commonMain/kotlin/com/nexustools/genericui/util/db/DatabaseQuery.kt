package com.nexustools.genericui.util.db

/**
 * Structured, enum/class-based database query with fluent builders and zero string query parsing.
 */
data class DatabaseQuery(
    val table: String,
    val selectedColumns: List<String> = emptyList(),
    val filter: DatabaseFilter? = null,
    val sorts: List<DatabaseSortSpec> = emptyList(),
    val limit: Int? = null,
    val offset: Int = 0
) {

    fun select(vararg columns: String): DatabaseQuery =
        copy(selectedColumns = columns.toList())

    fun where(newFilter: DatabaseFilter): DatabaseQuery =
        copy(filter = newFilter)

    fun andWhere(additionalFilter: DatabaseFilter): DatabaseQuery =
        copy(filter = filter?.let { DatabaseFilter.and(it, additionalFilter) } ?: additionalFilter)

    fun orWhere(additionalFilter: DatabaseFilter): DatabaseQuery =
        copy(filter = filter?.let { DatabaseFilter.or(it, additionalFilter) } ?: additionalFilter)

    fun orderBy(
        column: String,
        direction: DatabaseSortDirection = DatabaseSortDirection.ASCENDING
    ): DatabaseQuery = copy(sorts = sorts + DatabaseSortSpec(column, direction))

    fun limit(maxRows: Int?): DatabaseQuery =
        copy(limit = maxRows?.coerceAtLeast(0))

    fun offset(skipRows: Int): DatabaseQuery =
        copy(offset = skipRows.coerceAtLeast(0))

    /**
     * Evaluates this structured [DatabaseQuery] directly against an in-memory or cursor collection of
     * [DatabaseRecord]s without any string query parsing.
     */
    fun applyToRecords(records: Iterable<DatabaseRecord>): List<DatabaseRecord> {
        val activeFilter = filter
        var result = if (activeFilter == null) {
            records.toList()
        } else {
            records.filter { activeFilter.matches(it) }
        }

        if (sorts.isNotEmpty()) {
            result = result.sortedWith { a, b ->
                for (sort in sorts) {
                    val va = a[sort.column] ?: DatabaseValue.Null
                    val vb = b[sort.column] ?: DatabaseValue.Null
                    val cmp = va.compareTo(vb)
                    if (cmp != 0) {
                        return@sortedWith if (sort.direction == DatabaseSortDirection.ASCENDING) cmp else -cmp
                    }
                }
                0
            }
        }

        if (offset > 0) {
            result = if (offset >= result.size) emptyList() else result.drop(offset)
        }
        if (limit != null) {
            result = result.take(limit)
        }
        if (selectedColumns.isNotEmpty()) {
            result = result.map { it.project(selectedColumns) }
        }
        return result
    }

    companion object {
        fun from(table: String): DatabaseQuery = DatabaseQuery(table = table)
    }
}
