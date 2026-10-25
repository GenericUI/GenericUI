package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.layout.Container

open class Article(
    classPrefix: String = ""
) : Container(tagName = "article", classPrefix = classPrefix) {
    init {
        htmlTag = "article"
    }
}
