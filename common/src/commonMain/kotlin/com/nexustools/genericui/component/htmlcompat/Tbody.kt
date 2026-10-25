package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Tbody(
    classPrefix: String = ""
) : Container(tagName = "tbody", classPrefix = classPrefix) {
    init {
        htmlTag = "tbody"
    }
}
