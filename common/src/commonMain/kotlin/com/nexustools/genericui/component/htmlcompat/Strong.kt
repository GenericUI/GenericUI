package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Strong(
    text: String = "",
    classPrefix: String = "fw-bold"
) : Label(text = text, tagName = "strong", classPrefix = classPrefix) {
    init {
        htmlTag = "strong"
    }
}
