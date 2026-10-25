package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Figure(
    classPrefix: String = "figure"
) : Container(tagName = "figure", classPrefix = classPrefix) {
    init {
        htmlTag = "figure"
    }
}
