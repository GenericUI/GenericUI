package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H2(
    text: String = "",
    classPrefix: String = "h2"
) : Heading(text = text, level = 2, classPrefix = classPrefix) {
    init {
        htmlTag = "h2"
    }
}
