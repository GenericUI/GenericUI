package com.nexustools.genericui.component.typography

/**
 * Monospace preformatted code display block (`<pre><code>`).
 * For full interactive IDE code editing, use `:extensions:codeeditor` (`IdeCodeEditor`).
 */
open class CodeBlock(
    code: String = "",
    var languageHint: String = "text",
    tagName: String = "pre",
    classPrefix: String = "code-block font-monospace"
) : Label(
    text = code,
    tagName = tagName,
    classPrefix = classPrefix
) {
    init {
        htmlTag = tagName
    }
}
