package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H1(
    text: String = "",
    classPrefix: String = "h1"
) : Heading(text = text, level = 1, classPrefix = classPrefix) {
    init {
        htmlTag = "h1"
    }
}
