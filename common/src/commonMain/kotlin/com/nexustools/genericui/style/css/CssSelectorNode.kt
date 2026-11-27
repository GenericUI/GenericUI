package com.nexustools.genericui.style.css

/**
 * DOM-agnostic element tree node view used by [CssStyleSheet] to evaluate full W3C Selectors Level 4
 * (descendant, child `>`, adjacent sibling `+`, general sibling `~`, attribute matchers, `:nth-*`,
 * `:first-*`, `:last-*`, `:only-*`, `:empty`, `:root`, `:not()`, `:is()`, `:where()`, `:has()`,
 * pseudo-elements `::before`/`::after`, and inherited CSS custom properties `--*`) without coupling
 * `:common` to any specific rendering extension.
 */
interface CssSelectorNode {
    val selectorTagName: String
    val selectorId: String?
    val selectorClasses: Set<String>
    val selectorAttributes: Map<String, String>
    val selectorParent: CssSelectorNode?
    val selectorChildren: List<CssSelectorNode>
    val selectorHasTextContent: Boolean
        get() = false
    val selectorIsHovered: Boolean
        get() = false
    val selectorIsFocused: Boolean
        get() = false
    val selectorIsActive: Boolean
        get() = false
    val selectorIsEnabled: Boolean
        get() = true
    val selectorIsChecked: Boolean
        get() = false
    val selectorIsValid: Boolean
        get() = true
    val selectorIsPlaceholderShown: Boolean
        get() = false
    val selectorInheritedCustomProperties: Map<String, String>
        get() = emptyMap()

    fun getSelectorAttribute(name: String): String? {
        selectorAttributes[name]?.let { return it }
        val lower = name.lowercase()
        selectorAttributes[lower]?.let { return it }
        for ((k, v) in selectorAttributes) {
            if (k.equals(name, ignoreCase = true)) return v
        }
        return when (lower) {
            "id" -> selectorId
            "class" -> if (selectorClasses.isEmpty()) null else selectorClasses.joinToString(" ")
            "disabled" -> if (!selectorIsEnabled) "" else null
            "checked" -> if (selectorIsChecked) "" else null
            else -> null
        }
    }
}
