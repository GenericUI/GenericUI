package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Em(
    text: String = "",
    classPrefix: String = "fst-italic"
) : Label(text = text, tagName = "em", classPrefix = classPrefix) {
    init {
        htmlTag = "em"
    }
}
