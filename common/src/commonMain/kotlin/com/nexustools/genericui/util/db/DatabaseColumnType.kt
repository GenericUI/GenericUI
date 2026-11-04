package com.nexustools.genericui.util.db

/**
 * Column storage types supported by the structured cross-platform [com.nexustools.genericui.util.Database].
 */
enum class DatabaseColumnType {
    STRING,
    INT64,
    FLOAT64,
    BOOLEAN,
    BLOB;

    val sqlAffinity: String
        get() = when (this) {
            STRING -> "TEXT"
            INT64 -> "INTEGER"
            FLOAT64 -> "REAL"
            BOOLEAN -> "INTEGER"
            BLOB -> "BLOB"
        }
}
