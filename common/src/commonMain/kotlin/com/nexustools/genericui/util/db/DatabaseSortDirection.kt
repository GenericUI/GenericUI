package com.nexustools.genericui.util.db

/**
 * Sort direction for a [DatabaseSortSpec] clause in a [DatabaseQuery].
 */
enum class DatabaseSortDirection {
    ASCENDING,
    DESCENDING;

    val sqlKeyword: String
        get() = when (this) {
            ASCENDING -> "ASC"
            DESCENDING -> "DESC"
        }
}
