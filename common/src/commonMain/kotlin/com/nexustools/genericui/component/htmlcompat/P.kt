package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Text

open class P(
    text: String = "",
    classPrefix: String = ""
) : Text(text = text, paragraph = true, classPrefix = classPrefix) {
    init {
        htmlTag = "p"
    }
}
