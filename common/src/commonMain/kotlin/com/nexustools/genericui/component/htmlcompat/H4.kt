package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H4(
    text: String = "",
    classPrefix: String = "h4"
) : Heading(text = text, level = 4, classPrefix = classPrefix) {
    init {
        htmlTag = "h4"
    }
}
