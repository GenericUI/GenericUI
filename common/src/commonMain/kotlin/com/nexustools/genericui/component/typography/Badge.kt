package com.nexustools.genericui.component.typography

import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Bootstrap status pill/badge (`badge bg-primary`, etc.).
 */
class Badge(
    text: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    pill: Boolean = false
) : Label(
    text = text,
    tagName = "span",
    classPrefix = if (pill) "badge rounded-pill" else "badge"
) {
    init {
        this.type = type
    }
}
