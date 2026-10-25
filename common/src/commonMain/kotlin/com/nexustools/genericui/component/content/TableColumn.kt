package com.nexustools.genericui.component.content

/**
 * Column descriptor for a [Table].
 */
data class TableColumn(
    val id: String,
    val headerText: String,
    val sortable: Boolean = false,
    val widthPx: Int? = null
) {
    val htmlTag: String = "th"
}
