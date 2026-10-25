package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Td(
    text: String = "",
    classPrefix: String = ""
) : Container(tagName = "td", classPrefix = classPrefix) {
    init {
        htmlTag = "td"
        if (text.isNotEmpty()) {
            +text
        }
    }
}
