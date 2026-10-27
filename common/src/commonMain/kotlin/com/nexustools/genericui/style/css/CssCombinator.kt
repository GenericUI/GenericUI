package com.nexustools.genericui.style.css

/**
 * CSS selector combinator connecting two [CompiledSelectorSegment]s in a selector chain.
 */
enum class CssCombinator(val symbol: String) {
    DESCENDANT(" "),
    CHILD(">"),
    ADJACENT_SIBLING("+"),
    GENERAL_SIBLING("~")
}
