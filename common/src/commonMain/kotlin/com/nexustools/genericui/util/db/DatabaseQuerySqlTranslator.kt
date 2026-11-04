package com.nexustools.genericui.util.db

/**
 * Translates structured [DatabaseTableSchema], [DatabaseRecord], [DatabaseQuery], and [DatabaseFilter]
 * ASTs into parameterized SQLite statements ([DatabasePreparedSql]) using sanitized identifiers and `?`
 * bind placeholders—requiring **zero string query parsing**.
 */
object DatabaseQuerySqlTranslator {

    fun sanitizeIdentifier(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (cleaned.isEmpty() || cleaned[0].isDigit()) "t_$cleaned" else cleaned
    }

    fun quoteIdentifier(raw: String): String =
        "\"${sanitizeIdentifier(raw)}\""

    fun buildCreateTableSql(schema: DatabaseTableSchema): List<DatabasePreparedSql> {
        val statements = mutableListOf<DatabasePreparedSql>()
        val tableIdent = quoteIdentifier(schema.tableName)
        val colDefs = if (schema.columns.isEmpty()) {
            DatabaseTableSchema.defaultKeyValueColumns(schema.primaryKeyColumn)
        } else {
            schema.columns
        }

        val colsSql = colDefs.joinToString(", ") { col ->
            buildString {
                append(quoteIdentifier(col.name))
                append(' ')
                append(col.type.sqlAffinity)
                if (col.primaryKey) {
                    append(" PRIMARY KEY")
                } else {
                    if (!col.nullable) append(" NOT NULL")
                    if (col.unique) append(" UNIQUE")
                }
            }
        }
        statements.add(DatabasePreparedSql("CREATE TABLE IF NOT EXISTS $tableIdent ($colsSql)"))

        for (col in colDefs) {
            if (col.indexed && !col.primaryKey) {
                val idxName = quoteIdentifier("idx_${sanitizeIdentifier(schema.tableName)}_${sanitizeIdentifier(col.name)}")
                val colIdent = quoteIdentifier(col.name)
                statements.add(DatabasePreparedSql("CREATE INDEX IF NOT EXISTS $idxName ON $tableIdent ($colIdent)"))
            }
        }
        return statements
    }

    fun buildUpsertRecordSql(
        table: String,
        record: DatabaseRecord,
        primaryKeyColumn: String = "key"
    ): DatabasePreparedSql {
        val tableIdent = quoteIdentifier(table)
        val allValues = LinkedHashMap<String, DatabaseValue>()
        allValues[primaryKeyColumn] = DatabaseValue.of(record.key)
        for ((k, v) in record.values) {
            allValues[k] = v
        }
        val columns = allValues.keys.toList()
        val bindValues = columns.map { allValues[it] ?: DatabaseValue.Null }
        val colList = columns.joinToString(", ") { quoteIdentifier(it) }
        val placeholders = columns.joinToString(", ") { "?" }
        return DatabasePreparedSql(
            sql = "INSERT OR REPLACE INTO $tableIdent ($colList) VALUES ($placeholders)",
            bindValues = bindValues
        )
    }

    fun buildSelectSql(query: DatabaseQuery): DatabasePreparedSql {
        val tableIdent = quoteIdentifier(query.table)
        val selectCols = if (query.selectedColumns.isEmpty()) {
            "*"
        } else {
            query.selectedColumns.joinToString(", ") { quoteIdentifier(it) }
        }
        val bindValues = mutableListOf<DatabaseValue>()
        val sql = buildString {
            append("SELECT ")
            append(selectCols)
            append(" FROM ")
            append(tableIdent)
            query.filter?.let { f ->
                val whereClause = renderFilter(f, bindValues)
                if (whereClause.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClause)
                }
            }
            if (query.sorts.isNotEmpty()) {
                append(" ORDER BY ")
                append(
                    query.sorts.joinToString(", ") { sort ->
                        "${quoteIdentifier(sort.column)} ${sort.direction.sqlKeyword}"
                    }
                )
            }
            if (query.limit != null) {
                append(" LIMIT ")
                append(query.limit.coerceAtLeast(0))
                if (query.offset > 0) {
                    append(" OFFSET ")
                    append(query.offset)
                }
            } else if (query.offset > 0) {
                append(" LIMIT -1 OFFSET ")
                append(query.offset)
            }
        }
        return DatabasePreparedSql(sql = sql, bindValues = bindValues)
    }

    fun buildCountSql(query: DatabaseQuery): DatabasePreparedSql {
        val tableIdent = quoteIdentifier(query.table)
        val bindValues = mutableListOf<DatabaseValue>()
        val sql = buildString {
            append("SELECT COUNT(*) FROM ")
            append(tableIdent)
            query.filter?.let { f ->
                val whereClause = renderFilter(f, bindValues)
                if (whereClause.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClause)
                }
            }
        }
        return DatabasePreparedSql(sql = sql, bindValues = bindValues)
    }

    fun buildDeleteSql(query: DatabaseQuery): DatabasePreparedSql {
        val tableIdent = quoteIdentifier(query.table)
        val bindValues = mutableListOf<DatabaseValue>()
        val sql = buildString {
            append("DELETE FROM ")
            append(tableIdent)
            query.filter?.let { f ->
                val whereClause = renderFilter(f, bindValues)
                if (whereClause.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClause)
                }
            }
        }
        return DatabasePreparedSql(sql = sql, bindValues = bindValues)
    }

    private fun renderFilter(
        filter: DatabaseFilter,
        bindValues: MutableList<DatabaseValue>
    ): String {
        val logic = filter.logicalOp
        if (logic != null) {
            if (filter.children.isEmpty()) return ""
            val joiner = if (logic == DatabaseLogicalOp.AND) " AND " else " OR "
            val renderedChildren = filter.children.mapNotNull { child ->
                renderFilter(child, bindValues).takeIf { it.isNotEmpty() }
            }
            if (renderedChildren.isEmpty()) return ""
            if (renderedChildren.size == 1) return renderedChildren[0]
            return "(" + renderedChildren.joinToString(joiner) + ")"
        }

        val col = filter.column ?: return ""
        val op = filter.comparisonOp ?: return ""
        val colIdent = quoteIdentifier(col)

        return when (op) {
            DatabaseComparisonOp.IS_NULL -> "$colIdent IS NULL"
            DatabaseComparisonOp.IS_NOT_NULL -> "$colIdent IS NOT NULL"
            DatabaseComparisonOp.EQUALS -> {
                if (filter.value.isNull) {
                    "$colIdent IS NULL"
                } else {
                    bindValues.add(filter.value)
                    "$colIdent = ?"
                }
            }
            DatabaseComparisonOp.NOT_EQUALS -> {
                if (filter.value.isNull) {
                    "$colIdent IS NOT NULL"
                } else {
                    bindValues.add(filter.value)
                    "$colIdent != ?"
                }
            }
            DatabaseComparisonOp.LESS_THAN -> {
                bindValues.add(filter.value)
                "$colIdent < ?"
            }
            DatabaseComparisonOp.LESS_THAN_OR_EQUAL -> {
                bindValues.add(filter.value)
                "$colIdent <= ?"
            }
            DatabaseComparisonOp.GREATER_THAN -> {
                bindValues.add(filter.value)
                "$colIdent > ?"
            }
            DatabaseComparisonOp.GREATER_THAN_OR_EQUAL -> {
                bindValues.add(filter.value)
                "$colIdent >= ?"
            }
            DatabaseComparisonOp.STARTS_WITH -> {
                val rawPrefix = filter.value.asString() ?: ""
                bindValues.add(DatabaseValue.of("${escapeLikeWildcards(rawPrefix)}%"))
                "$colIdent LIKE ? ESCAPE '\\'"
            }
            DatabaseComparisonOp.CONTAINS -> {
                val rawSub = filter.value.asString() ?: ""
                bindValues.add(DatabaseValue.of("%${escapeLikeWildcards(rawSub)}%"))
                "$colIdent LIKE ? ESCAPE '\\'"
            }
            DatabaseComparisonOp.IN_LIST -> {
                if (filter.values.isEmpty()) {
                    "1 = 0"
                } else {
                    val placeholders = filter.values.joinToString(", ") {
                        bindValues.add(it)
                        "?"
                    }
                    "$colIdent IN ($placeholders)"
                }
            }
            DatabaseComparisonOp.BETWEEN -> {
                val upper = filter.secondValue ?: DatabaseValue.Null
                bindValues.add(filter.value)
                bindValues.add(upper)
                "$colIdent BETWEEN ? AND ?"
            }
        }
    }

    private fun escapeLikeWildcards(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
