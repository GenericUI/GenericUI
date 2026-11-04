package com.nexustools.genericui.util.db

/**
 * Column specification within a [DatabaseTableSchema].
 */
data class DatabaseColumnSpec(
    val name: String,
    val type: DatabaseColumnType,
    val primaryKey: Boolean = false,
    val nullable: Boolean = !primaryKey,
    val indexed: Boolean = false,
    val unique: Boolean = false
)
