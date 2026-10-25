package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Footer(
    classPrefix: String = ""
) : Container(tagName = "footer", classPrefix = classPrefix) {
    init {
        htmlTag = "footer"
    }
}
