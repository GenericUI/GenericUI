package com.nexustools.genericui.geometry

data class Size(val width: Int = 0, val height: Int = 0) {
    constructor(width: Float, height: Float) : this(width.toInt(), height.toInt())

    val isEmpty: Boolean
        get() = width <= 0 || height <= 0

    companion object {
        val ZERO = Size(0, 0)
    }
}
