package com.nexustools.genericui.geometry

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    constructor(x: Float, y: Float, width: Float, height: Float) : this(
        x = x.toInt(),
        y = y.toInt(),
        width = width.toInt(),
        height = height.toInt()
    )

    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun contains(px: Int, py: Int): Boolean =
        px in x until right && py in y until bottom
}
