package com.nexustools.genericui.component.typography

/**
 * Semantic section heading (`h1` .. `h6`).
 */
class Heading(
    text: String = "",
    val level: Int = 1
) : Label(
    text = text,
    tagName = "h${level.coerceIn(1, 6)}",
    classPrefix = "h${level.coerceIn(1, 6)}"
)
