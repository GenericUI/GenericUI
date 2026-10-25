package com.nexustools.genericui.component.typography

/**
 * Semantic section heading (`h1` .. `h6`).
 */
open class Heading(
    text: String = "",
    val level: Int = 1,
    classPrefix: String = "h${level.coerceIn(1, 6)}"
) : Label(
    text = text,
    tagName = "h${level.coerceIn(1, 6)}",
    classPrefix = classPrefix
) {
    init {
        htmlTag = "h${level.coerceIn(1, 6)}"
    }
}
