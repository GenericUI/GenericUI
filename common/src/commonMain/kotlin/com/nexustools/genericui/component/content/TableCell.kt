package com.nexustools.genericui.component.content

/**
 * Individual cell value inside a [TableRow].
 */
data class TableCell(
    val text: String
) {
    val htmlTag: String = "td"
}
