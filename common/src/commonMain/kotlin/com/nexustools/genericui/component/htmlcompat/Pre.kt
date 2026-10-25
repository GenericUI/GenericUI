package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.CodeBlock

open class Pre(
    code: String = "",
    languageHint: String = "text"
) : CodeBlock(code = code, languageHint = languageHint, tagName = "pre") {
    init {
        htmlTag = "pre"
    }
}
