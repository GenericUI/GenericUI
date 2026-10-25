package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.Label

open class Sup(
    text: String = "",
    classPrefix: String = ""
) : Label(text = text, tagName = "sup", classPrefix = classPrefix) {
    init {
        htmlTag = "sup"
    }
}
