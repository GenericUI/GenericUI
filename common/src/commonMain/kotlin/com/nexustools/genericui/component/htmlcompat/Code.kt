package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.typography.CodeBlock

open class Code(
    code: String = "",
    languageHint: String = "text"
) : CodeBlock(code = code, languageHint = languageHint, tagName = "code") {
    init {
        htmlTag = "code"
    }
}
