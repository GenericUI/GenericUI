package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Figcaption(
    text: String = "",
    classPrefix: String = "figure-caption"
) : Label(text = text, tagName = "figcaption", classPrefix = classPrefix) {
    init {
        htmlTag = "figcaption"
    }
}
