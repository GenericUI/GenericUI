package com.nexustools.genericui.util.db

/**
 * Structured comparison operators for [DatabaseFilter] predicates.
 */
enum class DatabaseComparisonOp {
    EQUALS,
    NOT_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    STARTS_WITH,
    CONTAINS,
    IN_LIST,
    BETWEEN,
    IS_NULL,
    IS_NOT_NULL
}
