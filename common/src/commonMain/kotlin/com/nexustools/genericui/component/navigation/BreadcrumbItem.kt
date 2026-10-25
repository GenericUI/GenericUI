package com.nexustools.genericui.component.navigation

/**
 * Single path segment inside a [Breadcrumb].
 */
data class BreadcrumbItem(
    val id: String,
    val label: String,
    val isActive: Boolean = false
) {
    val htmlTag: String = "genericui:breadcrumbitem"
}
