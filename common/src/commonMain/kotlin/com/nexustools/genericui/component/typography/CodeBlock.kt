package com.nexustools.genericui.component.typography

/**
 * Monospace preformatted code display block (`<pre><code>`).
 * For full interactive IDE code editing, use `:extensions:codeeditor` (`IdeCodeEditor`).
 */
class CodeBlock(
    code: String = "",
    var languageHint: String = "text"
) : Label(
    text = code,
    tagName = "pre",
    classPrefix = "code-block font-monospace"
)
