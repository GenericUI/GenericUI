package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H3(
    text: String = "",
    classPrefix: String = "h3"
) : Heading(text = text, level = 3, classPrefix = classPrefix) {
    init {
        htmlTag = "h3"
    }
}
