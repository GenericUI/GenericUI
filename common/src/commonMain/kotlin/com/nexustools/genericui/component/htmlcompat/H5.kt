package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Heading

open class H5(
    text: String = "",
    classPrefix: String = "h5"
) : Heading(text = text, level = 5, classPrefix = classPrefix) {
    init {
        htmlTag = "h5"
    }
}
