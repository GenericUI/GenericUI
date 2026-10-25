package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Sub(
    text: String = "",
    classPrefix: String = ""
) : Label(text = text, tagName = "sub", classPrefix = classPrefix) {
    init {
        htmlTag = "sub"
    }
}
