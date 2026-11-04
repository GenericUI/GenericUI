package com.nexustools.genericui.util.db

/**
 * Parameterized SQL statement and positional [bindValues] produced by [DatabaseQuerySqlTranslator]
 * from structured [DatabaseTableSchema], [DatabaseRecord], [DatabaseQuery], and [DatabaseFilter] objects.
 */
data class DatabasePreparedSql(
    val sql: String,
    val bindValues: List<DatabaseValue> = emptyList()
)
