package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.surface.ListGroup

open class Ol(
    classPrefix: String = "list-group list-group-numbered"
) : ListGroup(tagName = "ol", classPrefix = classPrefix) {
    init {
        htmlTag = "ol"
    }
}
