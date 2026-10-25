package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Details(
    classPrefix: String = ""
) : Container(tagName = "details", classPrefix = classPrefix) {
    init {
        htmlTag = "details"
    }
}
