package com.nexustools.genericui.component.typography

/**
 * Styled quotation block (`<blockquote class="blockquote">`).
 */
open class Blockquote(
    quote: String = "",
    var footerCitation: String? = null
) : Label(
    text = quote,
    tagName = "blockquote",
    classPrefix = "blockquote"
) {
    init {
        htmlTag = "blockquote"
    }
}
