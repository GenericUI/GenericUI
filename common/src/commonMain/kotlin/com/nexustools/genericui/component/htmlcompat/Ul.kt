package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.surface.ListGroup

open class Ul(
    classPrefix: String = "list-group"
) : ListGroup(tagName = "ul", classPrefix = classPrefix) {
    init {
        htmlTag = "ul"
    }
}
