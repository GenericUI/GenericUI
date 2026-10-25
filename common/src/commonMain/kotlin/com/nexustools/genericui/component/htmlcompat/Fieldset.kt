package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Fieldset(
    classPrefix: String = ""
) : Container(tagName = "fieldset", classPrefix = classPrefix) {
    init {
        htmlTag = "fieldset"
    }
}
