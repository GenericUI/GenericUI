package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Th(
    text: String = "",
    classPrefix: String = ""
) : Container(tagName = "th", classPrefix = classPrefix) {
    init {
        htmlTag = "th"
        if (text.isNotEmpty()) {
            +text
        }
    }
}
