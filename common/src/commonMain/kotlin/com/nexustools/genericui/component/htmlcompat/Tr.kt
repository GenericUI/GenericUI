package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Tr(
    classPrefix: String = ""
) : Container(tagName = "tr", classPrefix = classPrefix) {
    init {
        htmlTag = "tr"
    }
}
