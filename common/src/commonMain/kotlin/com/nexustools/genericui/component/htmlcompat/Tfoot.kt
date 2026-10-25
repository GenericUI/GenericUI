package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Tfoot(
    classPrefix: String = ""
) : Container(tagName = "tfoot", classPrefix = classPrefix) {
    init {
        htmlTag = "tfoot"
    }
}
