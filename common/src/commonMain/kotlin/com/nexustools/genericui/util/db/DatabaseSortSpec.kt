package com.nexustools.genericui.util.db

/**
 * Ordering specification for a single column in a [DatabaseQuery].
 */
data class DatabaseSortSpec(
    val column: String,
    val direction: DatabaseSortDirection = DatabaseSortDirection.ASCENDING
)
