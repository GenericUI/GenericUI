package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.surface.ListGroupItem

open class Li(
    text: String = "",
    id: String? = null,
    isActive: Boolean = false
) : ListGroupItem(id = id, isActive = isActive, tagName = "li") {
    init {
        htmlTag = "li"
        if (text.isNotEmpty()) {
            +text
        }
    }
}
