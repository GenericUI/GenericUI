package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Form(
    classPrefix: String = ""
) : Container(tagName = "form", classPrefix = classPrefix) {
    init {
        htmlTag = "form"
    }
}
