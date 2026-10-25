package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Thead(
    classPrefix: String = ""
) : Container(tagName = "thead", classPrefix = classPrefix) {
    init {
        htmlTag = "thead"
    }
}
