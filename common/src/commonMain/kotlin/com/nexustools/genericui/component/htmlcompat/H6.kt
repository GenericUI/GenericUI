package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H6(
    text: String = "",
    classPrefix: String = "h6"
) : Heading(text = text, level = 6, classPrefix = classPrefix) {
    init {
        htmlTag = "h6"
    }
}
