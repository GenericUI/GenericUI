package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Aside(
    classPrefix: String = ""
) : Container(tagName = "aside", classPrefix = classPrefix) {
    init {
        htmlTag = "aside"
    }
}
