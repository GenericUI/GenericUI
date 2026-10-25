package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Text

open class Span(
    text: String = "",
    classPrefix: String = ""
) : Text(text = text, paragraph = false, classPrefix = classPrefix) {
    init {
        htmlTag = "span"
    }
}
