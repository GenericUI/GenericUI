package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Header(
    classPrefix: String = ""
) : Container(tagName = "header", classPrefix = classPrefix) {
    init {
        htmlTag = "header"
    }
}
