package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class B(
    text: String = "",
    classPrefix: String = "fw-bold"
) : Label(text = text, tagName = "b", classPrefix = classPrefix) {
    init {
        htmlTag = "b"
    }
}
