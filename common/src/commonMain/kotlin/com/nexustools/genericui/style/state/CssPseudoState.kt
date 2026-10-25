package com.nexustools.genericui.style.state

/**
 * Standard CSS pseudo-class interactive states supported on GenericUI components.
 */
enum class CssPseudoState(val selectorSuffix: String) {
    DEFAULT(""),
    HOVER(":hover"),
    FOCUS(":focus"),
    ACTIVE(":active"),
    DISABLED(":disabled"),
    CHECKED(":checked"),
    FOCUS_VISIBLE(":focus-visible"),
    VISITED(":visited")
}
