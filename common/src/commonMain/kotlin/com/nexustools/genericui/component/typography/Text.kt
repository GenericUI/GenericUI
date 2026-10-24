package com.nexustools.genericui.component.typography

/**
 * Paragraph or inline body text component (`<p>` / `<span>`).
 */
open class Text(
    text: String = "",
    paragraph: Boolean = false
) : Label(
    text = text,
    tagName = if (paragraph) "p" else "span",
    classPrefix = ""
)
