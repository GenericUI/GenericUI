package com.nexustools.genericui.style.state

/**
 * Receiver scope for selector-targeted DSL styling (e.g., `".button" { style { ... } }`).
 */
class ComponentRuleScope(
    val selector: String
) {
    internal var styleBlock: ComponentStyleBlock? = null

    fun style(block: ComponentStyleBlock.() -> Unit) {
        val current = styleBlock ?: ComponentStyleBlock().also { styleBlock = it }
        current.block()
    }
}
