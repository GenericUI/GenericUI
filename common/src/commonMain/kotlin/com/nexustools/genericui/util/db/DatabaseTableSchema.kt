package com.nexustools.genericui.util.db

/**
 * Schema definition for a table in [com.nexustools.genericui.util.Database].
 */
data class DatabaseTableSchema(
    val tableName: String,
    val primaryKeyColumn: String = "key",
    val columns: List<DatabaseColumnSpec> = defaultKeyValueColumns(primaryKeyColumn)
) {
    companion object {
        fun defaultKeyValueColumns(primaryKeyColumn: String = "key"): List<DatabaseColumnSpec> = listOf(
            DatabaseColumnSpec(name = primaryKeyColumn, type = DatabaseColumnType.STRING, primaryKey = true, nullable = false),
            DatabaseColumnSpec(name = "value", type = DatabaseColumnType.BLOB, nullable = true),
            DatabaseColumnSpec(name = "updatedAt", type = DatabaseColumnType.INT64, nullable = false, indexed = true)
        )
    }
}
