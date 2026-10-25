package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Main(
    classPrefix: String = ""
) : Container(tagName = "main", classPrefix = classPrefix) {
    init {
        htmlTag = "main"
    }
}
