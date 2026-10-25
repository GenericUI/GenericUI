package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Small(
    text: String = "",
    classPrefix: String = "small"
) : Label(text = text, tagName = "small", classPrefix = classPrefix) {
    init {
        htmlTag = "small"
    }
}
