package com.nexustools.genericui.geometry

data class EdgeInsets(
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0
) {
    companion object {
        val ZERO = EdgeInsets(0, 0, 0, 0)
        fun all(value: Int): EdgeInsets = EdgeInsets(value, value, value, value)
        fun symmetric(vertical: Int = 0, horizontal: Int = 0): EdgeInsets =
            EdgeInsets(vertical, horizontal, vertical, horizontal)
    }
}
