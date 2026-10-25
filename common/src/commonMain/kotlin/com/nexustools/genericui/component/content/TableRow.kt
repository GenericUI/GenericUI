package com.nexustools.genericui.component.content

/**
 * Row model inside a [Table].
 */
data class TableRow(
    val id: String,
    val cells: List<TableCell>
) {
    val htmlTag: String = "tr"
}
