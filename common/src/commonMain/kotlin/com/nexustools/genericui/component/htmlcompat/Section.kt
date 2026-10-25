package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Section(
    classPrefix: String = ""
) : Container(tagName = "section", classPrefix = classPrefix) {
    init {
        htmlTag = "section"
    }
}
