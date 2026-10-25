package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Summary(
    text: String = "",
    classPrefix: String = ""
) : Label(text = text, tagName = "summary", classPrefix = classPrefix) {
    init {
        htmlTag = "summary"
    }
}
