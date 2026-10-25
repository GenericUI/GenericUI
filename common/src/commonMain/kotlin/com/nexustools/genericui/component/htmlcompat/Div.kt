package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Div(
    classPrefix: String = ""
) : Container(tagName = "div", classPrefix = classPrefix) {
    init {
        htmlTag = "div"
    }
}
