package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class I(
    text: String = "",
    classPrefix: String = "fst-italic"
) : Label(text = text, tagName = "i", classPrefix = classPrefix) {
    init {
        htmlTag = "i"
    }
}
